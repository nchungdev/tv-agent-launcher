package launcher

import (
	"fmt"
	"io"
	"strings"

	"github.com/nchungdev/tv-agent-launcher/apps/tvctl/internal/contract"
	"github.com/nchungdev/tv-agent-launcher/apps/tvctl/internal/tv"
)

func Screensaver(dev tv.Device, action string, out io.Writer) error {
	switch action {
	case "", "status":
		return showScreensaverStatus(dev, out)
	case "on":
		enableScreensaver(dev, out)
		return nil
	case "off":
		disableScreensaver(dev, out)
		return nil
	case "test":
		return testScreensaver(dev, out)
	default:
		return fmt.Errorf("unknown action %q (use on, off, status, test)", action)
	}
}

func enableScreensaver(dev tv.Device, out io.Writer) {
	service := tv.Launcher + "/" + contract.ClockDream
	dev.Shell("settings put secure screensaver_components " + service)
	dev.Shell("settings put secure screensaver_default_component " + service)
	dev.Shell("settings put secure screensaver_enabled 1")
	fmt.Fprintln(out, "Screensaver enabled: "+service)
}

func disableScreensaver(dev tv.Device, out io.Writer) {
	dev.Shell("settings put secure screensaver_enabled 0")
	fmt.Fprintln(out, "Screensaver disabled")
}

func testScreensaver(dev tv.Device, out io.Writer) error {
	if _, err := dev.Shell("service call dreams 1"); err != nil {
		return err
	}

	fmt.Fprintln(out, "Screensaver triggered")
	return nil
}

func showScreensaverStatus(dev tv.Device, out io.Writer) error {
	component, _ := dev.Shell("settings get secure screensaver_components")
	component = strings.TrimSpace(component)
	if component == "" || component == "null" {
		component = "none"
	}

	enabledRaw, _ := dev.Shell("settings get secure screensaver_enabled")
	enabled := "no"
	if strings.TrimSpace(enabledRaw) == "1" {
		enabled = "yes"
	}

	timeoutRaw, _ := dev.Shell("settings get system screen_off_timeout")
	timeout := strings.TrimSpace(timeoutRaw)
	if timeout == "" || timeout == "null" || timeout == "2147483647" {
		timeout = "never"
	} else {
		timeout = timeout + "ms"
	}

	fmt.Fprintf(out, "Screensaver component: %s\n", component)
	fmt.Fprintf(out, "Screensaver enabled:   %s\n", enabled)
	fmt.Fprintf(out, "Screen off timeout:    %s\n", timeout)
	return nil
}
