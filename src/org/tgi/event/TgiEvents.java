package org.tgi.event;

import java.util.List;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MMessage;
import org.compiere.model.MRole;
import org.compiere.model.MSession;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.osgi.service.event.Event;

public class TgiEvents extends AbstractEventHandler {

	private CLogger log = CLogger.getCLogger(TgiEvents.class);

	protected void initialize() {
		registerEvent(IEventTopics.AFTER_LOGIN);
	}

	protected void doHandleEvent(Event event) {
		//Check on which client we are and get the max concurrent session count
		int clientID = Env.getAD_Client_ID(Env.getCtx());
		int nbMaxSession = DB.getSQLValueEx(null, "SELECT MaxConcurrentSessions FROM AD_Client WHERE AD_Client_ID = ? AND IsLimitConcurrentSessions='Y'", clientID);

		log.info("Client "+clientID+" can have " + nbMaxSession + " concurrent session/s");

		//If there is a limit on concurrent session, check if we already reached the maximum amount
		if (nbMaxSession >= 0) {

			//If the user doesn't count on concurrent sessions, skip
			MUser user = MUser.get(Env.getCtx(), Env.getAD_User_ID(Env.getCtx()));
			if (user.get_ValueAsBoolean("IsDontCountMeInConcurrSessions"))
				return;

			//If the role doesn't count on concurrent sessions, skip
			MRole role = MRole.get(Env.getCtx(), Env.getAD_Role_ID(Env.getCtx()));
			if (role.get_ValueAsBoolean("IsDontCountMeInConcurrSessions"))
				return;

			//If the org doesn't count on concurrent sessions, skip. Since ad_org_id can be 0, we cannot use PO constructor
			if (DB.getSQLValueString(null, "SELECT IsDontCountMeInConcurrSessions FROM AD_Org WHERE AD_Org_ID = " + Env.getAD_Org_ID(Env.getCtx())).equalsIgnoreCase("Y"))
				return;

			List<MSession> sessions =  new Query(Env.getCtx(), MSession.Table_Name, "Processed = 'N'", null).setClient_ID().list();
			int realSessions = sessions.size();
			log.info("There is/are " + realSessions + " session/s on client " + clientID);

			//If there are already sessions, check which sessions really affect the count
			if (sessions.size() >= nbMaxSession) { 
				for (MSession session : sessions) {

					if (MUser.get(Env.getCtx(), session.getCreatedBy()).get_ValueAsBoolean("IsDontCountMeInConcurrSessions"))
						realSessions--;
					else if (MRole.get(Env.getCtx(), session.getAD_Role_ID()).get_ValueAsBoolean("IsDontCountMeInConcurrSessions"))
						realSessions--;
					else if (DB.getSQLValueString(null, "SELECT IsDontCountMeInConcurrSessions FROM AD_Org WHERE AD_Org_ID = " + session.getAD_Org_ID()).equalsIgnoreCase("Y"))
						realSessions--;
					else if (session.getAD_Session_ID() == Env.getContextAsInt(Env.getCtx(), "#AD_Session_ID"))
						realSessions--;
				}	
				log.info("There is/are " + realSessions + " session/s who affect the count");
				int messageID = DB.getSQLValue(null, "SELECT ConcurrentSessionsMsg_ID FROM AD_Client WHERE AD_Client_ID = " + clientID);
				String msg = messageID > 0 ? new MMessage(Env.getCtx(), messageID, null).getMsgText() : "You can't log because too much users are already logged in !";

				if (realSessions>=nbMaxSession) { 
					log.info("There are already to many sessions. User is not allowed to log in");
					addErrorMessage(event, msg);
				}
			}
		}
	}
}