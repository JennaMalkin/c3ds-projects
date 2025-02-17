/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.data.babel.ctos;

import java.nio.ByteBuffer;

import natsue.data.IOUtils;
import natsue.data.babel.UINUtils;

public class CTOSGetConnectionDetail extends TargetUIDCTOS {
	@Override
	public String toString() {
		return "CTOSGetConnectionDetail{of: " + UINUtils.toString(targetUIN) + "}";
	}

	/**
	 * Makes an OK response without IP/port. You can use the dummy response otherwise.
	 */
	public byte[] makeOkResponse() {
		ByteBuffer bb = IOUtils.newBuffer(32);
		bb.putInt(BASE_FIELD_TICKET, ticketNumber);
		bb.putInt(BASE_FIELD_E, 1);
		return bb.array();
	}

	@Override
	public int transactionDummyLength() {
		return 32;
	}
}
