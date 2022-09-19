/*
 * c3ds-projects - Assorted compatibility fixes & useful tidbits
 * Written starting in 2022 by contributors (see CREDITS.txt)
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package natsue.server.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import natsue.log.ILogProvider;
import natsue.log.ILogSource;

/**
 * JDBC-based Natsue database implementation.
 * REMEMBER: STUFF HERE CAN BE ACCESSED FROM MULTIPLE THREADS.
 */
public class JDBCNatsueDatabase implements INatsueDatabase, ILogSource {
	private final ILogProvider logParent;
	private final Connection database;
	private final PreparedStatement stmUserByUID;
	private final PreparedStatement stmUserByUsername;
	private final PreparedStatement stmUserByNickname;
	private final PreparedStatement stmStoreOnSpool;
	private final PreparedStatement stmDeleteFromSpool;
	private final PreparedStatement stmGetFromSpool;
	private final PreparedStatement stmEnsureCreature;
	private final PreparedStatement stmEnsureCreatureEvent;
	private final PreparedStatement stmCreateUser;

	public JDBCNatsueDatabase(ILogProvider ilp, Connection conn) throws SQLException {
		database = conn;
		logParent = ilp;
		JDBCMigrate.migrate(database, this);
		stmUserByUID = conn.prepareStatement("SELECT uid, username, psha256 FROM natsue_users WHERE uid=?");
		stmUserByUsername = conn.prepareStatement("SELECT uid, nickname, psha256 FROM natsue_users WHERE username=?");
		stmUserByNickname = conn.prepareStatement("SELECT uid, nickname, psha256 FROM natsue_users WHERE nickname_folded=?");
		stmStoreOnSpool = conn.prepareStatement("INSERT INTO natsue_spool(uid, data) VALUES (?, ?)");
		stmDeleteFromSpool = conn.prepareStatement("DELETE FROM natsue_spool WHERE id=? and uid=?");
		stmGetFromSpool = conn.prepareStatement("SELECT id, uid, data FROM natsue_spool WHERE uid=?");
		stmEnsureCreature = conn.prepareStatement("INSERT INTO natsue_history_creatures(moniker, first_uid, ch0, ch1, ch2, ch3, ch4) VALUES (?, ?, ?, ?, ?, ?, ?)");
		stmEnsureCreatureEvent = conn.prepareStatement("INSERT INTO natsue_history_events(event_id, sender_uid, moniker, event_index, event_type, world_time, age_ticks, unix_time, unknown, param1, param2, world_name, world_id, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
		stmCreateUser = conn.prepareStatement("INSERT INTO natsue_users(uid, username, nickname, nickname_folded, psha256) VALUES (?, ?, ?, ?, ?)");
	}

	@Override
	public ILogProvider getLogParent() {
		return logParent;
	}

	@Override
	public String getConfigString(String name, String defaultVal) {
		return defaultVal;
	}

	private UserInfo getUserFromResultSet(ResultSet rs) throws SQLException {
		if (!rs.next()) {
			rs.close();
			return null;
		}
		UserInfo ui = new UserInfo(rs.getString(2), rs.getString(3), rs.getInt(1));
		rs.close();
		return ui;
	}

	@Override
	public UserInfo getUserByUID(int uid) {
		synchronized (this) {
			try {
				stmUserByUID.setInt(1, uid);
				return getUserFromResultSet(stmUserByUID.executeQuery());
			} catch (Exception ex) {
				log(ex);
				return null;
			}
		}
	}

	@Override
	public UserInfo getUserByFoldedUsername(String username) {
		synchronized (this) {
			try {
				stmUserByUsername.setString(1, username);
				return getUserFromResultSet(stmUserByUsername.executeQuery());
			} catch (Exception ex) {
				log(ex);
				return null;
			}
		}
	}

	@Override
	public UserInfo getUserByFoldedNickname(String nickname) {
		synchronized (this) {
			try {
				stmUserByNickname.setString(1, nickname);
				return getUserFromResultSet(stmUserByNickname.executeQuery());
			} catch (Exception ex) {
				log(ex);
				return null;
			}
		}
	}

	@Override
	public void spoolMessage(int uid, byte[] pm) {
		synchronized (this) {
			try {
				stmStoreOnSpool.setInt(1, uid);
				stmStoreOnSpool.setBytes(2, pm);
				stmStoreOnSpool.executeUpdate();
			} catch (Exception ex) {
				log(ex);
			}
		}
	}

	@Override
	public byte[] popFirstSpooledMessage(int uid) {
		byte[] message = null;
		synchronized (this) {
			try {
				stmGetFromSpool.setInt(1, uid);
				ResultSet rs = stmGetFromSpool.executeQuery();
				if (rs.next()) {
					long id = rs.getLong(1);
					message = rs.getBytes(3);
					// and now remove from the spool
					stmDeleteFromSpool.setLong(1, id);
					stmDeleteFromSpool.setInt(2, uid);
					stmDeleteFromSpool.execute();
				}
				rs.close();
			} catch (Exception ex) {
				log(ex);
			}
		}
		return message;
	}

	@Override
	public void ensureCreature(String moniker, int firstUID, int ch0, int ch1, int ch2, int ch3, int ch4) {
		synchronized (this) {
			try {
				stmEnsureCreature.setString(1, moniker);
				stmEnsureCreature.setInt(2, firstUID);
				stmEnsureCreature.setInt(3, ch0);
				stmEnsureCreature.setInt(4, ch1);
				stmEnsureCreature.setInt(5, ch2);
				stmEnsureCreature.setInt(6, ch3);
				stmEnsureCreature.setInt(7, ch4);
				stmEnsureCreature.executeUpdate();
			} catch (Exception ex) {
				// This is expected to happen, so discard
			}
		}
	}

	@Override
	public void ensureCreatureEvent(int senderUID, String moniker, int index, int type, int worldTime, int ageTicks, int unixTime, int unknown, String param1, String param2, String worldName, String worldID, String userID) {
		synchronized (this) {
			try {
				stmEnsureCreatureEvent.setString(1, moniker + "." + index);
				stmEnsureCreatureEvent.setInt(2, senderUID);
				stmEnsureCreatureEvent.setString(3, moniker);
				stmEnsureCreatureEvent.setInt(4, index);
				stmEnsureCreatureEvent.setInt(5, type);
				stmEnsureCreatureEvent.setInt(6, worldTime);
				stmEnsureCreatureEvent.setInt(7, ageTicks);
				stmEnsureCreatureEvent.setInt(8, unixTime);
				stmEnsureCreatureEvent.setInt(9, unknown);
				stmEnsureCreatureEvent.setString(10, param1);
				stmEnsureCreatureEvent.setString(11, param2);
				stmEnsureCreatureEvent.setString(12, worldName);
				stmEnsureCreatureEvent.setString(13, worldID);
				stmEnsureCreatureEvent.setString(14, userID);
				stmEnsureCreatureEvent.executeUpdate();
			} catch (Exception ex) {
				// This is expected to happen, so discard
			}
		}
	}

	@Override
	public boolean tryCreateUser(int uid, String username, String nickname, String nicknameFolded, String passwordHash) {
		synchronized (this) {
			try {
				stmCreateUser.setInt(1, uid);
				stmCreateUser.setString(2, username);
				stmCreateUser.setString(3, nickname);
				stmCreateUser.setString(4, nicknameFolded);
				stmCreateUser.setString(5, passwordHash);
				stmCreateUser.executeUpdate();
			} catch (Exception ex) {
				// This is expected to happen, so discard
				return false;
			}
		}
		return true;
	}
}