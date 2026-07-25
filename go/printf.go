package lalein

// A minimal printf pass supporting the placeholders translation files
// actually use: %d, %s, %f, %x/%X, %% and positional references like %2$d.
// Width/precision/flags are not supported (yet) — the Java version delegates
// to String.format, here we keep the footprint tiny.

import (
	"errors"
	"strconv"
	"strings"
)

func sprintf(format string, args []any) (string, error) {
	var out strings.Builder
	out.Grow(len(format) + 16)
	sequential := 0
	i := 0
	for i < len(format) {
		if format[i] != '%' {
			start := i
			for i < len(format) && format[i] != '%' {
				i++
			}
			out.WriteString(format[start:i])
			continue
		}
		i++
		if i >= len(format) {
			return "", errors.New("dangling '%' at end of format string")
		}
		if format[i] == '%' {
			out.WriteByte('%')
			i++
			continue
		}
		// Optional positional reference: digits followed by '$'.
		explicit := -1
		j := i
		for j < len(format) && format[j] >= '0' && format[j] <= '9' {
			if explicit < 0 {
				explicit = 0
			}
			explicit = explicit*10 + int(format[j]-'0')
			j++
		}
		if explicit > 0 && j < len(format) && format[j] == '$' {
			explicit-- // 1-based to 0-based
			i = j + 1
		} else {
			explicit = -1
		}
		if i >= len(format) {
			return "", errors.New("dangling '%' at end of format string")
		}
		if explicit == -1 {
			explicit = sequential
			sequential++
		}
		if explicit >= len(args) {
			return "", errors.New("format string references argument #" + strconv.Itoa(explicit+1) +
				" but only " + strconv.Itoa(len(args)) + " given")
		}
		arg := args[explicit]
		switch format[i] {
		case 'd', 'i':
			if v, ok := arg.(int); ok {
				out.WriteString(strconv.Itoa(v))
			} else if f, ok := asNumber(arg); ok && f == float64(int64(f)) && abs64(f) < 9e15 {
				out.WriteString(strconv.FormatInt(int64(f), 10))
			} else {
				return "", errors.New("%d requires an integral argument at position #" + strconv.Itoa(explicit+1))
			}
		case 's':
			switch v := arg.(type) {
			case nil:
				out.WriteString("null")
			case string:
				out.WriteString(v)
			case interface{ String() string }:
				out.WriteString(v.String())
			case error:
				out.WriteString(v.Error())
			default:
				if f, ok := asNumber(arg); ok {
					out.WriteString(doubleToString(f))
				} else {
					return "", errors.New("%s requires a string-able argument at position #" + strconv.Itoa(explicit+1))
				}
			}
		case 'f':
			if f, ok := asNumber(arg); ok {
				out.WriteString(strconv.FormatFloat(f, 'f', 6, 64))
			} else {
				return "", errors.New("%f requires a numeric argument at position #" + strconv.Itoa(explicit+1))
			}
		case 'x', 'X':
			if f, ok := asNumber(arg); ok && f == float64(int64(f)) {
				hex := strconv.FormatInt(int64(f), 16)
				if format[i] == 'X' {
					hex = upperHex(hex)
				}
				out.WriteString(hex)
			} else {
				return "", errors.New("%x requires an integral argument at position #" + strconv.Itoa(explicit+1))
			}
		default:
			return "", errors.New("unsupported conversion '%" + string(format[i]) + "' — supported: %d %s %f %x %%")
		}
		i++
	}
	return out.String(), nil
}

// doubleToString mimics Java's Double.toString for the %s conversion:
// integral values keep a trailing ".0" ("5.0", not "5").
func doubleToString(v float64) string {
	if v == float64(int64(v)) && abs64(v) < 1e15 {
		return strconv.FormatFloat(v, 'f', 1, 64)
	}
	return strconv.FormatFloat(v, 'g', -1, 64)
}

func upperHex(s string) string {
	b := []byte(s)
	for i, c := range b {
		if c >= 'a' && c <= 'f' {
			b[i] = c - ('a' - 'A')
		}
	}
	return string(b)
}

func abs64(v float64) float64 {
	if v < 0 {
		return -v
	}
	return v
}

func floorHalfUp(d float64) float64 {
	f := float64(int64(d + 0.5))
	if f > d {
		return f - 1
	}
	return f
}
