# STOC
C_TID_BASE
----------

Note that this acts as a "base" for other packets.

32 bytes


* +0: Type (***but see +20***)
	* char type
		* Note that unlike on client, which is a bit confusing, this is always a single byte.
	* [CBabelClient](../Structs/CBabelClient.md)::Connectx
		* 0x0A: Handshake Response - C_TID_HANDSHAKE_RESPONSE
			* Shows up as part of the connection process and nowhere else (it's not handled in [CBabelDConnection](../Structs/CBabelDConnection.md)::Handle)
			* This particular handler interprets the first (lowest) byte of the type as a char, using the second byte as an error code.
	* [CBabelDConnection](../Structs/CBabelDConnection.md)::Handle
		* 0x09: MessageDispatch - C_TID_MESSAGE_STOC
		* 0x0D: UserOnline - C_TID_USER_LINE
		* 0x0E: UserOffline - C_TID_USER_LINE
		* 0x0F: GetClientInfo transaction response, see CTOS
		* 0x13: GetConnectionDetail transaction response, see CTOS
		* 0x14: ClientCommand - C_TID_CLIENT_COMMAND
		* 0x1D: OnlineChange - C_TID_ONLINE_CHANGE
			* *likely deprecated or internal, see packet details for more information*
		* 0x1E: VirtualCircuitConnect - C_TID_VIRTUAL_CONNECT
		* 0x1F: VirtualCircuit - C_TID_VIRTUAL_CIRCUIT
		* 0x20: VirtualCircuitClose - C_TID_VIRTUAL_CIRCUIT_CLOSE
		* 0x21 through 0x24 inclusive: STOC shunts around weirdly, may imply something odd going on in [CBabelDConnection](../Structs/CBabelDConnection.md)
			* No evidence these are used here, but there's the implication that this is how the CTOS Docking Station-specific "Get Random User" packet is handled, at least conceptually.
		* 0x2A: Migrate - C_TID_MIGRATE
* +1: 3 bytes of padding.
	* char padding[3]
* +4: Field A
* +8: Field B
* +12: Field C
* +16: Field D
* +20: ***If non-zero, client will pass this to TicketDispatch. Please see the appropriate CTOS Response section.***
	* int ticketDispatch
	* Worth noting: The size of a response packet is determined by the transaction ticket's inherent response size (*decided by the transaction starter*) + furtherData. See:
		* [CBabelTransactionTicket](../Structs/CBabelTransactionTicket.md)::SignalTicket
		* [CBabelDConnection](../Structs/CBabelDConnection.md)::Handle
* +24: Usually indicates the length of further data after the fixed-size portion (but only in packets with variable-length further data).
	* int furtherData
* +28: Field E


C_TID_MESSAGE_STOC
------------------


* Name is a guess
* Has a CTOS equivalent but it is not the same format!


This is used to send PRAY data. It might do some other things, but that's what the packet dumps show.
In particular, this packet type is how Norns are sent over the Warp!

32 bytes + message data


* Type: 0x09
* A/B: Ignored
* C/D: Ignored
* Ticket number: Ignored
* Further data: Length of the following [Formats:Packed Babel Message](../Formats/Packed_Babel_Message.md) in bytes. Note this includes its header.
* E: Ignored


This is followed by the actual message as described with the length.
*NOTE: The sender HID/UID is embedded in the Packed Babel Message. Server should be checking these!*


* Received by [CBabelDConnection](../Structs/CBabelDConnection.md)::MessageDispatch
* Example: dsprotocol/pk/norn1
* Example: dsprotocol/pk/chat1


C_TID_HANDSHAKE_RESPONSE
------------------------


* Name is a guess


The response to the CTOS C_TID_HANDSHAKE.

48 bytes + data


* Type: 0x0A
	* char type - not int!
* +1: Error code
	* unsigned char errorCode
	* 0: Connecting to the server failed but internet connection seems okay
	* 1: Nickname already logged on
	* 2: Internal Error
	* 3: Invalid nickname/password
	* 4: Internal Error
	* 5: Internal Error
	* 6: Invalid nickname/password
	* 7: You or CL server not connected to internet
	* 8: Internal Error
	* 9: Internal Error
	* 10: Internal Error
	* 11: Internal Error
	* 12: Too many users connected
	* 13: You or CL server not connected to internet
	* 14: Docking Station needs an update (This is *never* going to happen, but I think it's the reasoning behind the 1/2/0 numbers in the handshake).
	* 15: You or CL server not connected to internet
	* 16: Unknown Error (distinct from Internal Error)
	* 17: (...This seems to continue for a while)
* A/B: Server UID/HID - if UID is 0, considered an error.
* C/D: Client's new UID/HID
* Ticket number: Ignored
* Further data: Ignored
* E: Ignored
* (...Ignored...)
* +44: Additional data length
	* int addDataLen


Followed by additional data:

* +0: If not 1, server list updates are disabled.
	* int servInfoFlagA
* +4: If not 1, server list updates are disabled.
	* int servInfoFlagB
* +8: Amount of server info records.

Each server info record (representing a [CBabelServerInfo](../Structs/CBabelServerInfo.md)):

* +0: Port
* +4: ID
* +8: Two zero-terminated strings, the IP address and server name.



* Received by [CBabelClient](../Structs/CBabelClient.md)::Connectx
* Example: dsprotocol/pk/login-ok1


C_TID_USER_LINE
---------------


* Name is a guess


Informs the client that a user has gone online or offline, with corresponding data.
*Notably, the Babel side of the client does not adjust it's "online user" information.*

32 bytes + additional data


* Type: 0x0D for online, 0x0E for offline
* A/B: Ignored
* C/D: Ignored
* Ticket number: Ignored
* Further data: Length of ensuing object.
* E: Ignored

Followed by a [Packed Babel Short User Data Object.](../Formats/Packed_Babel_Short_User_Data.md)


* Received by [CBabelDConnection](../Structs/CBabelDConnection.md)::UserOnline / [CBabelDConnection](../Structs/CBabelDConnection.md)::UserOffline
* Example: don.dump packet number 11 (for online), 1577 (for offline).


C_TID_ONLINE_CHANGE
-------------------


* Name is a guess


Informs the client that a user has gone online or offline, without corresponding data.
*Notably, the Babel side of the client adjusts it's "online user" information, but does not provide a detailed ClientMessage (only a simple "online users changed" message).*
*Also notable is that there are no known examples of this packet, which may imply it is deprecated or internal.*

32 bytes


* Type: 0x1D
* A/B: Ignored
* C/D: User UID/HID
* Ticket number: Ignored
* Further data: Ignored
* E: If non-zero, the user is going online. If zero, the user is going offline.



* Received by [CBabelDConnection](../Structs/CBabelDConnection.md)::OnlineChange
* *No known example*


C_TID_VIRTUAL_CIRCUIT_CLOSE
---------------------------


* Name is a guess


Informs the client that all virtual circuits with the given UID/HID pair are being disconnected.
As the client never *seems* to send one of these packets, and since no sign of VSNs shows up, it can only be assumed that this is for if the remote client disconnects.

32 bytes


* Type: 0x20
* A/B: Ignored
* C/D: Sender's UID/HID
* Ticket number: Ignored
* Further data: Ignored
* E: Ignored



* Received by [CBabelDConnection](../Structs/CBabelDConnection.md)::VirtualCircuitClose
* *No known example*


C_TID_MIGRATE
-------------


* Name is a guess


Client dials ctlcpc180.cyberlife.co.uk - fat chance of getting *that *back up.
This entry is only here for completeness's sake.
It looks like there's a secondary login mechanism in the servers for migrating clients using 0x2B (CTOS) and 0x2C (STOC).
Relevant code is in CBabelMigrateConnection::AttemptMigrate.
If you really want to investigate that further, do it yourself!

32 bytes


* Type: 0x2A
* A/B: Ignored
* C/D: User UID/HID
* Ticket number: Ignored
* Further data: Ignored
* E: Passed to the AttemptMigrate function as what I can only assume is some sort of authentication token.



* Received by [CBabelDConnection](../Structs/CBabelDConnection.md)::Migrate
* *No known example*


