/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rals.tooling;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import rals.caos.CAOSUtils;

/**
 * CAOS into the game at maximum velocity!
 * Note that the library here is particularly lacking in features. 
 */
public class Injector {
	public static String cpxRequest(String req) throws IOException {
		boolean isDefaults = true;
		String host = System.getenv("CPX_HOST");
		if ((host == null) || (host.equals(""))) {
			host = "127.0.0.1";
		} else {
			isDefaults = false;
		}
		String port = System.getenv("CPX_PORT");
		if ((port == null) || (port.equals(""))) {
			port = "19960";
		} else {
			isDefaults = false;
		}

		if (isDefaults && checkIfLikelyWindows()) {
			// This is a HORRIBLE thing.
			try (RandomAccessFile raf = new RandomAccessFile("\\\\.\\pipe\\CAOSWorkaroundBecauseWindowsIsAFuckedUpPieceOfShit", "rw")) {
				return cpxRequestInternal(req, raf, raf, null);
			} catch (IOException ioe) {
				// Any IOException (SPECIFICALLY) from the above implies we need to retry the request.
				// That said, if we're even doing this and failed... print the stack trace to stderr.
				// If you're using an up-to-date caosprox you shouldn't GET this error in these conditions, so.
				ioe.printStackTrace();
			}
		}

		try (Socket cpxSocket = new Socket(host, Integer.parseInt(port))) {
			DataOutputStream dos = new DataOutputStream(cpxSocket.getOutputStream());
			return cpxRequestInternal(req, new DataInputStream(cpxSocket.getInputStream()), dos, dos);
		}
	}
	// this is stupid and dumb, frankly.
	private static boolean checkIfLikelyWindows() {
		String res = System.getProperty("os.name");
		if (res == null)
			return false;
		return res.toLowerCase().contains("win");
	}
	private static String cpxRequestInternal(String req, DataInput inp, DataOutput oup, OutputStream needsFlushing) throws IOException {
		byte[] data = req.getBytes(CAOSUtils.CAOS_CHARSET);
		ByteBuffer tmp = ByteBuffer.allocate(48);
		tmp.order(ByteOrder.LITTLE_ENDIAN);
		tmp.putInt(0, data.length + 1);
		try {
			oup.write(tmp.array(), 0, 4);
			oup.write(data);
			oup.write(0);
			if (needsFlushing != null)
				needsFlushing.flush();
		} catch (Exception ex) {
			// Deliberately ignore this and wait for the reads to fail.
			// This can happen if we had the connection closed on us early.
		}
		// Now that the request is sent...
		inp.readFully(tmp.array());
		int resultCode = tmp.getInt(32);
		int resultLen = tmp.getInt(36);
		byte[] resultData = new byte[resultLen];
		inp.readFully(resultData);
		int cutPoint = resultData.length;
		for (int i = 0; i < resultData.length; i++) {
			if (resultData[i] == 0) {
				cutPoint = i;
				break;
			}
		}
		String resultText = new String(resultData, 0, cutPoint, CAOSUtils.CAOS_CHARSET);
		if (resultCode != 0)
			throw new CPXException(resultText);
		return resultText;
	}
	public static class CPXException extends RuntimeException {
		private static final long serialVersionUID = -6375845826233160433L;

		public CPXException(String text) {
			super(text);
		}
	}
}
