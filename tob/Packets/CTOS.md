# CTOS
C_TID_BASE
----------

Note that this acts as a "base" for other packets.

32 bytes


* +0: Type
	* int type
		* This is really really weird. It's treated as either char[4] or an int depending on the whim of the function.
	* 0x09: MessageDispatch - C_TID_MESSAGE_CTOS
	* 0x0F: GetClientInfo - C_TID_GET_CLIENT_INFO
	* 0x10: AddWWREntry - C_TID_WWR
	* 0x11: RemoveWWREntry - C_TID_WWR
	* 0x12: NotifyListeningPort - C_TID_NOTIFY_LISTENING_PORT
	* 0x13: GetConnectionDetail - C_TID_GET_CONNECTION_DETAIL
	* 0x14: ClientCommand - C_TID_CLIENT_COMMAND
	* 0x18: GetStatus - C_TID_GET_STATUS
	* 0x1E: VirtualCircuitConnect - C_TID_VIRTUAL_CONNECT
	* 0x1F: VirtualCircuit - C_TID_VIRTUAL_CIRCUIT
	* 0x0221: DSFetchRandomUser - C_TID_DS_FETCH_RANDOM_USER
	* 0x0321: DSFeedHistory - C_TID_DS_FEED_HISTORY
	* 0x25: Handshake - C_TID_HANDSHAKE
* +4: Field A (usually server UID)
* +8: Field B (usually server HID)
* +12: Field C (usually a UID, target's or user's)
* +16: Field D (usually a HID, target's or user's)
* +20: Ticket number (if non-zero, the packet expects a response) - see [STOC](./STOC.md) logic of this field for how this works.
	* int ticketDispatch
	* If non-zero, a response from the server is expected.
	* The code is very certain to never allocate ticket ID 0, as that would not appear as a response to STOC logic.
* +24: Usually indicates the length of further data (but only in packets with variable-length further data).
	* int furtherData
* +28: Field E


C_TID_MESSAGE_CTOS
------------------


* Name is a guess
* Has a STOC equivalent but it is not the same format!


This in practice is used for [C2E Message](../Formats/C2E_Message.md) sending (unless the API feels like using a NetDirectLink).
It's the main "workload" packet.

40 bytes + message data


* Type: 0x09
* A/B: Server UID/HID
* C/D: User UID/HID (as if from [CBabelClient](../Structs/CBabelClient.md)::GetUser)
* Ticket number: 0
* Further data: Length of the following [Formats:Packed Babel Message](../Formats/Packed_Babel_Message.md) in bytes. Note this includes its header.
* E: 0
* +32: Target UID/HID
	* int targetUID, targetHID


This is followed by the actual message as described with the length.


* Sent by [CBabelClient](../Structs/CBabelClient.md)::SendBinaryMessage
* Example: don.dump packet number 29


C_TID_GET_CLIENT_INFO
---------------------


* Name is a guess
* Transactional


Used to turn an abstract UID/HID pair into meaningful details, i.e. NET: ULIN and NET: UNIK.

32 bytes


* Type: 0x0F
* A/B: Server UID/HID
* C/D: Target UID/HID
* Ticket number: Allocated
* Further data: 0
* E: Boolean parameter to function - as seemingly usual, always 0


### Response

32 bytes + further data


* Type: 0x0F/Ignored
* A/B: Ignored
* C/D: Ignored
* Ticket number: Expected
* Further data: Size of the following user data, or 0 to indicate failure.
* E: Ignored


Followed by nothing (failure) or a [Packed Babel Short User Data](../Formats/Packed_Babel_Short_User_Data.md) (success).


* Sent by [CBabelClient](../Structs/CBabelClient.md)::GetClientInfo
* Example: don.dump packet numbers 18/19


C_TID_WWR
---------


* Name is a guess


Tells the server that the client particularly cares if this user is online or offline.
The server will thus send C_TID_USER_LINE updates.

32 bytes


* Type: 0x10 for add, 0x11 for remove
	* int type
* A/B: Server UID/HID
* C/D: Target UID/HID
* Ticket number: 0
* Further data: 0
* E: 0



* Sent by [CBabelClient](../Structs/CBabelClient.md)::AddWWREntry / CBabelClient::RemoveWWREntry
* Example: don.dump packet number 10, which immediately leads to an STOC C_TID_USER_LINE in packet 11.


C_TID_NOTIFY_LISTENING_PORT
---------------------------


* Name is a guess


Used to notify the server about the port on which the client listens for incoming peer links.
***It's worth noting that the client code will never actually send this - SetPeerListener is a NOP which always fails.***
***But the peer link mechanism itself might still be in use, as VirtualCircuit-related functions still use it.***

32 bytes


* Type: 0x12
* A/B: Server UID/HID
* C/D: 0
* Ticket number: 0
* Further data: 0
* E: Parameter to NotifyListeningPort - presumably would have been a port number.



* Sent by [CBabelClient](../Structs/CBabelClient.md)::NotifyListeningPort
* *No known example*


C_TID_GET_CONNECTION_DETAIL
---------------------------


* Name is a guess
* Transactional


32 bytes


* Type: 0x13
* A/B: Server UID/HID
* C/D: Target UID/HID
* Ticket number: Allocated
* Further data: 0
* E: A parameter? Seems to always be false, so 0.

 shows up as packet number 16/17 in don.dump.

### Response

32 bytes


* Type: Ignored
* A: IP address *in network order.* That is, passable directly to inet_ntoa - lowest byte is first in the IP address.
* B: *Presumably* port
* C/D: Ignored
* Ticket number: Expected
* Further data: Discarded
* E: GetConnectionDetail goes to the trouble of retrieving this as a short, but it's discarded by all known callers.


Worth noting: The response is considered a success if and only if at least one of these two cases occur:

* A and B are not zero
* E is not zero



* Sent by [CBabelClient](../Structs/CBabelClient.md)::GetConnectionDetail
* *No known example*


C_TID_GET_STATUS
----------------


* Name is a guess
* Transactional


32 bytes


* Type: 0x18
* A/B: Server UID/HID
* C/D: 0
* Ticket number: allocated
* Further data: 0
* E: 0


### Response

48 bytes


* Type: Ignored
* A/B: Ignored
* C/D: Ignored
* Ticket number: Expected
* Further data: Discarded
* +32: See [C_BABEL_STATUS](../Structs/C_BABEL_STATUS.md).



* Sent by [CBabelClient](../Structs/CBabelClient.md)::GetStatus
* *No known example*


C_TID_DS_FETCH_RANDOM_USER
--------------------------


* Name is a guess
* Transactional


Pretty much the core of NET: RUSO.

32 bytes


* Type: 0x0221
* A/B: Server UID/HID
* C/D: User UID/HID (as if from [CBabelClient](../Structs/CBabelClient.md)::GetUser)
* Ticket number: Allocated
* Further data: 0
* E: 3


### Response

32 bytes


* Type: Ignored
* A/B: Ignored
* C/D: Response UID/HID
* Ticket number: Expected
* Further data: Discarded
* E: Expected to be 1 for success (*and therefore otherwise is failure!*)



* Sent by [DSNetManager](../Structs/DSNetManager.md)::AsyncDSFetchRandomUser
* *No known example*


C_TID_DS_FEED_HISTORY
---------------------


* Name is a guess
* Transactional


Used to upload Creature History.
Note that the reason this is a transaction is because the caller really really wants to make sure the server actually *got* these events.
Otherwise bad stuff could happen, like the server missing events.


* Type: 0x0321
* A/B: Server UID/HID
* C/D: User UID/HID (as if from [CBabelClient](../Structs/CBabelClient.md)::GetUser)
* Ticket number: Allocated
* Further data: The size of the creature history blob.
* E: 0


This is followed by the creature history blob, described at [Creature History Blob](../Formats/Creature_History_Blob.md)

### Response

32 bytes


* Type: Ignored
* A/B: Ignored
* C/D: Ignored
* Ticket number: Expected
* Further data: Discarded
* E: Ignored



* Sent by [DSNetManager](../Structs/DSNetManager.md)::DSFeedHistory
* Example: don.dump packet number 210/211


C_TID_HANDSHAKE
---------------


* Name is a guess


The first data sent on the connection.
This provides the username and password for login.

52 bytes + additional bytes


* Type: 0x25
* A/B: 0
* C/D: Current UID/HID (as if from [CBabelClient](../Structs/CBabelClient.md)::GetUser)
* Ticket number: Allocated
* Further data: 0
* E: 0
* +32: 1
	* int one
* +36: 2
	* int two
* +40: 0
	* int zero
* +44: Length of zero-terminated username
	* int usernameLen
	* Just to clarify, this length includes the null terminator, so an empty username would still be 1 here.
* +48: Length of zero-terminated password
	* int passwordLen
	* Just to clarify, this length includes the null terminator, so an empty password would still be 1 here.


Username and password, with zero terminators, follow.


* Sent by [CBabelClient](../Structs/CBabelClient.md)::Connect(char const *,char const *,char const *,int,bool,bool)
* Example: don.dump packet number 1


