# Retro-Commander
Retro-Commander is a graphical application developed in Java and JavaFX that implements a specific UART serial port protocol to communicate with external standalone devices. 
This app implements a custom protocol to provide an emulation of all primary I/O interfaces to avoid the necessity of other external hardware like monitors,
keyboards and hard disk/floppy disks.

# Why this application has been developing?

In last years, I've made a lot of electronic boards that contain mircoprocessors like Intel 8085 and some I/O devices. There is one in particular that contains basical interfaces to interact with users (PS/2 keyboard interface, a NTSC video card and a floppy disk controller for store data) that need to be tested many times. 

In order to write and test assembly programs, is necessary to create a computer application that can both manage a terminal interface and a disk emulator with reliability.

# The algorithm 

Retro-commander is programmed, in facts, to interact to the external device using a RS232 serial port with a specific algorithm based on packet communication. A single packet is simply composed by a certain amount of bytes that have different functions.

In particular, a transmission of a single packet is made using this order:
* **start marker**, a byte with a predefined value. It's used by the algorithm to understand if there is an incoming transmission from the receive line.
* **header**, one byte which contains all general information about the packet.
* **command**, a byte that indicates a specific value used by the receiver to understand how the packet should be used.
* **checksum**, a byte used for verify the integrity of the packet.
* **body**, which contains all data and can assume different dimension (from 0 to 31 bytes).
* **end marker**, a byte with a predefined value used for identifying the end of the transmitted packet.

The header byte is coded using four different criteria:
*   **ACK**, bit 8 that indicates if the packet is an acknowledge (setted) or not.
*   **count**, bit 7 used for distinguish a packet from a retransmission
*   **type**, bit 6 used for indicate a slow or a fast packet.
*   **lenght**, all remaining bits used to identify the dimension of the body of the packet.

The protocol connects the external device, considered as a **master**, and the java application, considered as a **slave**. In facts, all control is given to the external device, which can sends or request data from and to the slave. In the other hand, the slave is considered as a normal I/O device that executes all tasks. 

In base of this division, all communication are divided in two different types.



## Layout description
When the appication is launched, the Terminal tab automatically will appear on the screen. In this tab, you can start a communication with your device and interact with a terminal-like interface. This black space, when selected, will automatically transmit all key pressed from the keyboard and print all received characters.


![main view](/docs/terminal-view.png)

The Communication tab contains all settings of serial port (device name and baudrate) and all settings dedicated to the disk emulation.

![main view](/docs/settings.view.png)
