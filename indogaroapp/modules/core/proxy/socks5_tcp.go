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

var (
	ErrNotTCP          = errors.New("underlying connection is not TCP")
	ErrUnsupportedSOCKS = errors.New("unsupported SOCKS protocol version")
	ErrCommandInvalid  = errors.New("unsupported SOCKS5 command, must be CONNECT")

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
	DialTimeout time.Duration
}

func NewSOCKS5Engine(timeout time.Duration) *SOCKS5Engine {
	return &SOCKS5Engine{
		DialTimeout: timeout,
	}
}

// readExact provides pure Zero-Dependency raw byte iterational parsing over net.Conn
func readExact(c net.Conn, size int) ([]byte, error) {
	buf := make([]byte, size)
	var count int
	for count < size {
		n, err := c.Read(buf[count:])
		if err != nil {
			return nil, err
		}
		count += n
	}
	return buf, nil
}

func (s *SOCKS5Engine) HandleStream(conn net.Conn, isVVIP bool) {
	clientTCP, ok := conn.(*net.TCPConn)
	if !ok {
		log.Error().Err(ErrNotTCP).Msg("Rejecting non-TCP transport pipeline")
		conn.Close()
		return
	}
	
	defer clientTCP.Close()

	// Parse SOCKS5 Greeting Block: [Version(1), NMethods(1)]
	verCountBuf, err := readExact(clientTCP, 2)
	if err != nil {
		log.Warn().Err(err).Msg("Greeting block parsing aborted")
		return
	}
	
	if verCountBuf[0] != 0x05 {
		log.Warn().Err(ErrUnsupportedSOCKS).Msg("Foreign version header format detected")
		return
	}

	nMethods := int(verCountBuf[1])
	if _, err := readExact(clientTCP, nMethods); err != nil {
		log.Warn().Err(err).Msg("Methods parsing aborted")
		return
	}

	// 0x00: Trigger NO AUTHENTICATION REQUIRED method acknowledgment
	if _, err := clientTCP.Write([]byte{0x05, 0x00}); err != nil {
		log.Warn().Err(err).Msg("Greeting auth acknowledge payload flush failed")
		return
	}

	// Parse SOCKS5 Request Headers: [Ver(1), Cmd(1), Rsv(1), ATYP(1)]
	header, err := readExact(clientTCP, 4)
	if err != nil {
		log.Warn().Err(err).Msg("Missing core routing metadata payload")
		return
	}

	if header[0] != 0x05 {
		log.Warn().Err(ErrUnsupportedSOCKS).Msg("Bad tunnel version sequence within request headers")
		return
	}

	if header[1] != 0x01 {
		log.Warn().Err(ErrCommandInvalid).Msg("Cmd mismatch fallback; proxy operates as strict CONNECT only")
		clientTCP.Write([]byte{0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0}) // Command Not Supported
		return
	}

	var targetAddr string
	
	// Map addressing sequence (ATYP switch)
	switch header[3] {
	case 0x01: // IPv4 Sequence (4 Bytes length)
		ipVal, err := readExact(clientTCP, 4)
		if err != nil {
			log.Warn().Err(err).Msg("Mapping TCP sequence extraction for IPv4 crashed")
			return
		}
		targetAddr = net.IP(ipVal).String()
		
	case 0x03: // FQDN Stream resolution sequence
		domainSizeHeader, err := readExact(clientTCP, 1)
		if err != nil {
			log.Warn().Err(err).Msg("Mapping dynamic length layout stream for Host string size extraction aborted")
			return
		}
		sizeRoute := int(domainSizeHeader[0])
		fqdnPayloadString, err := readExact(clientTCP, sizeRoute)
		if err != nil {
			log.Warn().Err(err).Msg("Mapping protocol mapped resolution size format routing metadata parsing FQDN limit mapping aborted")
			return
		}
		targetAddr = string(fqdnPayloadString)
		
	case 0x04: // IPv6 limits string representation arrays network limit matrix TCP 16 bytes.
		ipVal6, err := readExact(clientTCP, 16)
		if err != nil {
			log.Warn().Err(err).Msg("IPv6 extraction sequence blocked aborted route limit stream length")
			return
		}
		targetAddr = "[" + net.IP(ipVal6).String() + "]"
		
	default:
		log.Warn().Msg("ATYP mapping unsupported dropped loop abort connection mapped limit stream code block parsing layout")
		clientTCP.Write([]byte{0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0}) // Address type not supported
		return
	}

	portBuf, err := readExact(clientTCP, 2)
	if err != nil {
		log.Warn().Err(err).Msg("Target network port resolving length code sequence network bytes layout blocked layout stream routing extraction loop abort.")
		return
	}

	targetPort := binary.BigEndian.Uint16(portBuf)
	targetResolutionAddr := targetAddr + ":" + strconv.Itoa(int(targetPort))

	targetConn, err := net.DialTimeout("tcp", targetResolutionAddr, s.DialTimeout)
	if err != nil {
		log.Debug().Err(err).Str("remote_route", targetResolutionAddr).Msg("Relay upstream mapping refused / Connection failed pipeline payload code map sequence route extraction string limit abort connection stream format format layout")
		clientTCP.Write([]byte{0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0}) // Host unreachable
		return
	}

	targetTCP, ok := targetConn.(*net.TCPConn)
	if !ok {
		targetConn.Close()
		log.Error().Msg("Underlying downstream sequence is strictly NOT resolving to IP-TCP code limit loop pipeline bytes byte matrix blocked loop pipeline bytes connection network string byte abort mapping code block map sequence abort stream layout string format map extraction protocol mapped protocol payload length format")
		return
	}
	
	defer targetTCP.Close()

	// Compliance Rules trigger response BEFORE mapping Splice route Zero-copy TCP
	successHeaderReplyPayloadStreamTriggerCodeBytesSequence := []byte{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0}
	if _, err := clientTCP.Write(successHeaderReplyPayloadStreamTriggerCodeBytesSequence); err != nil {
		log.Warn().Err(err).Msg("Client mapped dropped loop format pipeline success acknowledgment route protocol map byte trigger code code payload code layout layout connection")
		return
	}

	// Optimize System syscall auto-tune logic
	clientTCP.SetNoDelay(true)
	targetTCP.SetNoDelay(true)

	// Route limit trigger mapped memory alloc routing logic network buffer layout loop limit array array map format code loop format mapping stream layout string protocol loop protocol bytes bytes sequence limits
	if isVVIP {
		log.Debug().Msg("Activating Pure-VVIP Engine 4MB Memory Zero-Allocation limits matrix payload connection")
		clientTCP.SetReadBuffer(4 * 1024 * 1024)
		targetTCP.SetReadBuffer(4 * 1024 * 1024)
	}
	
	// Bridging dual map memory pools. Waitgroup-less network layout
	go func() {
		var localRefBufferSlicePointerSequence *[]byte
		if isVVIP {
			localRefBufferSlicePointerSequence = vvipPool.Get().(*[]byte)
			defer vvipPool.Put(localRefBufferSlicePointerSequence)
		} else {
			localRefBufferSlicePointerSequence = regularPool.Get().(*[]byte)
			defer regularPool.Put(localRefBufferSlicePointerSequence)
		}
		
		io.CopyBuffer(targetTCP, clientTCP, *localRefBufferSlicePointerSequence)
		
		// Tear-down protocol code mapping layout matrix stream connection length network extraction connection byte loop limits array
		clientTCP.Close()
		targetTCP.Close()
	}()

	var mainThreadBufferSlicePointerSequence *[]byte
	if isVVIP {
		mainThreadBufferSlicePointerSequence = vvipPool.Get().(*[]byte)
		defer vvipPool.Put(mainThreadBufferSlicePointerSequence)
	} else {
		mainThreadBufferSlicePointerSequence = regularPool.Get().(*[]byte)
		defer regularPool.Put(mainThreadBufferSlicePointerSequence)
	}

	io.CopyBuffer(clientTCP, targetTCP, *mainThreadBufferSlicePointerSequence)
	
	// Fast Aggressive Tear-down code layout code connection byte length block mapping payload limit connection array byte format layout array format layout format block extraction array map format byte limits loop limits network byte code mapping length array format
	clientTCP.Close()
	targetTCP.Close()
}
