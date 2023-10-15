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

**ACK** and **count** are two flags dedicated to the flow control. In particular, a single packer can be transmitted in two different ways:



The protocol connects the external device, considered as a **master**, and the java application, considered as a **slave**. In facts, all control is given to the external device, which can sends or request data from and to the slave. In the other hand, the slave is considered as a normal I/O device that executes all tasks. 

In base of this division, all communication are divided in two different scenarios.

![data_share](/docs/data_share.png)

In the first case, the master wants to receive data from the slave:
* First, the master sends a packet with a specific command and data to signal the slave that has to send data.
* Then, the slave receives the packet and, in base of the data and the command received, executes all needed operations and respond with one or more data packets with the same command.
* At this point, the master waits all packets from the slave and then continue his normal execution

in the second scenario, the master want to send a certain amount of data to the slave:
* First, the master sends a packet with a specific command and data to signal the slave that has to receive one or more packets.
* Then, the master sends one or more packets that contains all data that has to be trasmitted.
* At this point, the slave waits all packet from the master.

During normal transmission, the byte **command** of all packets should remain the same. The master and the slave can share data with different commands, in base of the type of operation that both have to do:
* **Connection reset (0x21)**, when the master wants to synchronize with slave. in this case, the master sends an empty packet and the slave should synchronize his **count bit state**.

* **Send board ID (0x22)**, when the master sends his ID to the slave. In this case, the master only sends a packet which contains his ID to the slave.
  
* **Terminal send byte (0x01)**, when the master wants to send one or more chars to the slave console.
  In this case, the master uses the initial command packet's body to send also chars. In this case, the slave should wait only the first packet.
* **Terminal request byte (0x02)**, when the master requests chars from the slave console.
  In this case, the first command packet is transmitted without body and the slave can respond with only a single data packet if there are available chars from the console. In this case, the master should wait for a certain time if the slave responds or not.
* **Data request informations (0x11)**, when the master requests disk emulator status. In this case, the master sends an empty packet ad the slave responds with a packet that contains datas in the following order:
  ```
  bytes per sector (1 byte coded in 128 multiples), sectors per track (one byte), tracks per head (two bytes coded in little endian), number of heads (one byte),disk state (one byte)
  ```
  Disk disk state is a single byte composed by different flags:
  ```
  inserted( bit 8) + ready( bit 7) + read only ( bit 6 ) + transfer error ( bit 5 ) + seek error ( bit 4 )
  ```
  Insert flag indicates that the disk emulation is on, ready flag indicats that the disk emulator is ready to send or receive sectors and other bits are setted only when there was an error during a past disk operation.

* **Data read sector (0x12)**, when the master request a sector from the disk emulator. In this case, the master sends the first packet with these disk coordinates:
  ```
  sector number(one byte) + track number(two bytes little endian) + head number (one byte)
  ```
  At this point, the slave should repond with more 16 bytes packets that contain the entire sector requested or an empty packet to signal to the master that there is an error. In the second case, the master should request information to understand why the slave cannot respond propely.

* **Data write sector (0x13)**, when the master sends a sector to the disk emulator. In this case, the master sends the first packet with these disk coordinates:
  ```
  sector number(one byte) + track number(two bytes little endian) + head number (one byte)
  ```
  Then, the master sends more packet that contains all entire sector. Next, the master should request information to verify if the slave has received all data correctly.
  

## Layout description
When the appication is launched, the Terminal tab automatically will appear on the screen. In this tab, you can start a communication with your device and interact with a terminal-like interface. This black space, when selected, will automatically transmit all key pressed from the keyboard and print all received characters.


![main view](/docs/terminal-view.png)

The Communication tab contains all settings of serial port (device name and baudrate) and all settings dedicated to the disk emulation.

![main view](/docs/settings.view.png)
