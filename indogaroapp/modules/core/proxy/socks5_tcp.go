package proxy

import (
	"encoding/binary"
	"errors"
	"io"
	"net"
	"strconv"
	"sync"
	"time"

	"github.com/rs/zerolog/log"
)

const (
	socksVer   = 0x05
	cmdConnect = 0x01
	atypIPv4   = 0x01
	atypDomain = 0x03
	atypIPv6   = 0x04
)

var (
	ErrInvalidSocksVersion = errors.New("unsupported SOCKS protocol version")
	ErrInvalidCommand      = errors.New("unsupported command, only CONNECT allowed")
	ErrAddressError        = errors.New("address resolution error")
	
	regularPool = sync.Pool{
		New: func() any {
			b := make([]byte, 32*1024)
			return &b
		},
	}

	vvipPool = sync.Pool{
		New: func() any {
			b := make([]byte, 4*1024*1024)
			return &b
		},
	}
)

type SOCKS5Engine struct {
	ConnectTimeout time.Duration
}

func NewSOCKS5Engine() *SOCKS5Engine {
	return &SOCKS5Engine{
		ConnectTimeout: 10 * time.Second,
	}
}

// readExact implements Rule 7: I/O Mentah. Zero dependency I/O, iteratif memanggil net.Conn.Read murni
func readExact(conn net.Conn, size int) ([]byte, error) {
	buffer := make([]byte, size)
	var readIdx int
	for readIdx < size {
		n, err := conn.Read(buffer[readIdx:])
		if err != nil {
			return nil, err
		}
		readIdx += n
	}
	return buffer, nil
}

func (e *SOCKS5Engine) HandleConnection(conn net.Conn, isVVIP bool) {
	clientTCP, ok := conn.(*net.TCPConn)
	if !ok {
		log.Error().Msg("Client is not using raw TCP sockets, discarding")
		conn.Close()
		return
	}
	
	defer clientTCP.Close()

	// Initial Handshake / Greeting
	greetingHeader, err := readExact(clientTCP, 2)
	if err != nil {
		log.Warn().Err(err).Msg("Failed reading SOCKS greeting")
		return
	}

	if greetingHeader[0] != socksVer {
		log.Warn().Msg("Invalid SOCKS5 version block")
		return
	}

	nMethods := int(greetingHeader[1])
	_, err = readExact(clientTCP, nMethods)
	if err != nil {
		log.Warn().Err(err).Msg("Failed reading SOCKS methods")
		return
	}

	// 0x00 No Authentication Required
	_, err = clientTCP.Write([]byte{socksVer, 0x00})
	if err != nil {
		log.Warn().Err(err).Msg("Failed answering greeting handshake")
		return
	}

	// Read Client Request Command
	reqHeader, err := readExact(clientTCP, 4)
	if err != nil {
		log.Warn().Err(err).Msg("Failed reading command headers")
		return
	}

	if reqHeader[0] != socksVer {
		log.Warn().Msg("Invalid command protocol version")
		return
	}

	if reqHeader[1] != cmdConnect {
		log.Warn().Msg("Engine restricted exclusively to TCP CONNECT relays")
		// Replying Cmd not supported
		clientTCP.Write([]byte{socksVer, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
		return
	}

	atyp := reqHeader[3]
	var targetAddr string

	// Extract Target based on ATYP
	switch atyp {
	case atypIPv4:
		ipBuf, err := readExact(clientTCP, 4)
		if err != nil {
			log.Warn().Err(err).Msg("Error parsing IPv4")
			return
		}
		targetAddr = net.IP(ipBuf).String()
	case atypDomain:
		lenBuf, err := readExact(clientTCP, 1)
		if err != nil {
			log.Warn().Err(err).Msg("Error parsing FQDN length")
			return
		}
		domainLen := int(lenBuf[0])
		fqdnBuf, err := readExact(clientTCP, domainLen)
		if err != nil {
			log.Warn().Err(err).Msg("Error parsing FQDN string")
			return
		}
		targetAddr = string(fqdnBuf)
	case atypIPv6:
		ipBuf, err := readExact(clientTCP, 16)
		if err != nil {
			log.Warn().Err(err).Msg("Error parsing IPv6")
			return
		}
		targetAddr = "[" + net.IP(ipBuf).String() + "]"
	default:
		log.Warn().Msg("ATYP Invalid format dropped")
		clientTCP.Write([]byte{socksVer, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
		return
	}

	portBuf, err := readExact(clientTCP, 2)
	if err != nil {
		log.Warn().Err(err).Msg("Failed decoding port metadata")
		return
	}

	portInt := binary.BigEndian.Uint16(portBuf)
	dest := targetAddr + ":" + strconv.Itoa(int(portInt))

	targetConn, err := net.DialTimeout("tcp", dest, e.ConnectTimeout)
	if err != nil {
		// Replying network failure / Host unreachable
		clientTCP.Write([]byte{socksVer, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
		log.Debug().Err(err).Str("host", dest).Msg("Target network refused pipeline")
		return
	}

	targetTCP, ok := targetConn.(*net.TCPConn)
	if !ok {
		targetConn.Close()
		log.Error().Msg("Dest dial yield invalid IP-TCP matrix type")
		return
	}
	defer targetTCP.Close()

	// Kepatuhan Standar (Rule 1): Reply SOCKS5 0x00 (SUCCESS) 
	successResp := []byte{socksVer, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}
	_, err = clientTCP.Write(successResp)
	if err != nil {
		log.Warn().Err(err).Msg("Relay success stream trigger aborted, broken client pipeline")
		return
	}

	e.startRelayProcess(clientTCP, targetTCP, isVVIP)
}

func (e *SOCKS5Engine) startRelayProcess(client *net.TCPConn, target *net.TCPConn, isVVIP bool) {
	// Rule 3: Mengamankan TCP latensi / Optimasi Kernel 
	client.SetNoDelay(true)
	target.SetNoDelay(true)

	// Rule 5: Modulasi Pool 32KB/4MB - Prevent fragmentation enkripsi tingkat VIP.
	var bufClientToTargetPtr, bufTargetToClientPtr *[]byte

	if isVVIP {
		// MTProto VVIP Special Override Memory Logic. Fix 4MB limit allocation overhead socket
		client.SetReadBuffer(4 * 1024 * 1024)
		target.SetReadBuffer(4 * 1024 * 1024)

		bufClientToTargetPtr = vvipPool.Get().(*[]byte)
		defer vvipPool.Put(bufClientToTargetPtr)

		bufTargetToClientPtr = vvipPool.Get().(*[]byte)
		defer vvipPool.Put(bufTargetToClientPtr)
		log.Debug().Msg("VVIP Node Routing 4MB Zero-Alloc Splice Engine Engaged")
	} else {
		// General Auto-Tune Logic Node Socket Layer
		bufClientToTargetPtr = regularPool.Get().(*[]byte)
		defer regularPool.Put(bufClientToTargetPtr)

		bufTargetToClientPtr = regularPool.Get().(*[]byte)
		defer regularPool.Put(bufTargetToClientPtr)
	}

	// Deref pool memory buffer for io block slice.
	bClient2Target := *bufClientToTargetPtr
	bTarget2Client := *bufTargetToClientPtr

	// Execute dual multiplex bridging async with EOF Immediate Kill strategy. 
	// Rule 2 & 4 Applied
	go func() {
		io.CopyBuffer(target, client, bClient2Target) // Triggers Kernel Zero-Copy Splice syscall (linux)
		
		// Rule 2 Teardown Trigger - when EOF happens one-way pipeline ends the opposite immediately via Force-Close 
		target.Close()
		client.Close()
	}()

	io.CopyBuffer(client, target, bTarget2Client) // Triggers Kernel Zero-Copy Splice syscall (linux)
	
	// Rule 2 Teardown trigger for return bridge
	client.Close()
	target.Close()
}
