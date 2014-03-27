package org.tgi.event;

import java.util.List;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MOrg;
import org.compiere.model.MRole;
import org.compiere.model.MSession;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.osgi.service.event.Event;

public class TgiEvents extends AbstractEventHandler {

	protected void initialize() {
		registerEvent(IEventTopics.AFTER_LOGIN);
	}

	protected void doHandleEvent(Event event) {
		if (event.getTopic().equals(IEventTopics.AFTER_LOGIN)) {

			int clientID = Env.getAD_Client_ID(Env.getCtx());
			int nbMaxSession = DB.getSQLValueEx(null, "SELECT MaxConcurrentSessions FROM AD_Client WHERE AD_Client_ID = ? AND IsLimitConcurrentSessions='Y'", clientID);

			System.out.println("Connections authorized for client : " + nbMaxSession);

			if (nbMaxSession >= 0) {
				List<MSession> sessions =  new Query(Env.getCtx(), MSession.Table_Name, "Processed = 'N'", null)
				.setClient_ID()
				.list();

				int realSessions = sessions.size();
				System.out.println("real sessions : " + realSessions);

				if (sessions.size() > nbMaxSession) { // besoin de vérifier s'il faut enlever des sessions
					for (MSession session : sessions) {

						if (MUser.get(Env.getCtx(), session.getCreatedBy()).get_ValueAsBoolean("DontCountMe"))
							realSessions--;
						else if (MRole.get(Env.getCtx(), session.getAD_Role_ID()).get_ValueAsBoolean("DontCountMe"))
							realSessions--;
						else if (MOrg.get(Env.getCtx(), session.getAD_Org_ID()).get_ValueAsBoolean("DontCountMe"))
							realSessions--;
						else if (session.getAD_Session_ID() == Env.getContextAsInt(Env.getCtx(), "#AD_Session_ID"))
							realSessions--;
					}	

					System.out.println("real sessions (minus dontcountme) : " + realSessions);

					String msg = "You can't log because too much users are already logged in !"; // Aller chercher le msg dans AD_Client
					// on utilise ce msg hardcodé si aucun msg défini

					if (realSessions>=nbMaxSession) // subtract 1 as the current user is already in AD_Session
						addErrorMessage(event, msg);
				}
			}
		}
	}
}