# Retro-Commander
Retro-Commander is a graphical application developed in Java and JavaFX that implements a specific UART serial port protocol to communicate with external standalone devices. 
This app implements a custom protocol to provide an emulation of all primary I/O interfaces to avoid the necessity of other external hardware like monitors,
keyboards and hard disk/floppy disks.

## Layout description
When the appication is launched, the Terminal tab automatically will appear on the screen. In this tab, you can start a communication with your device and interact with a terminal-like interface. This black space, when selected, will automatically transmit all key pressed from the keyboard and print all received characters.


![main view](/docs/terminal-view.png)

The Communication tab contains all settings of serial port (device name and baudrate) and all settings dedicated to the disk emulation.

![main view](/docs/settings.view.png)
