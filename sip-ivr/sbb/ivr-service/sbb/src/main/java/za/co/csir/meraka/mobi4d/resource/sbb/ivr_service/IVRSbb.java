/*
 * IVR SBB.
 */
package za.co.csir.meraka.mobi4d.resource.sbb.ivr_service;

import javax.slee.serviceactivity.ServiceActivity;
import javax.slee.serviceactivity.ServiceActivityFactory;
import javax.slee.nullactivity.NullActivity;
import javax.slee.nullactivity.NullActivityFactory;
import javax.slee.nullactivity.NullActivityContextInterfaceFactory;

import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.message.CreateConnection;
import jain.protocol.ip.mgcp.message.CreateConnectionResponse;
import jain.protocol.ip.mgcp.message.DeleteConnection;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.NotificationRequestResponse;
import jain.protocol.ip.mgcp.message.Notify;
import jain.protocol.ip.mgcp.message.NotifyResponse;
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConflictingParameterException;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionMode;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.EventName;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
import jain.protocol.ip.mgcp.message.parms.RequestedAction;
import jain.protocol.ip.mgcp.message.parms.RequestedEvent;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;
import jain.protocol.ip.mgcp.pkg.MgcpEvent;
import jain.protocol.ip.mgcp.pkg.PackageName;

import org.mobicents.protocols.mgcp.jain.pkg.AUMgcpEvent;
import org.mobicents.protocols.mgcp.jain.pkg.AUPackage;

import java.text.ParseException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.sip.Dialog;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.ActivityContextInterface;
import javax.slee.CreateException;
import javax.slee.FactoryException;
import javax.slee.RolledBackContext;
import javax.slee.Sbb;
import javax.slee.SbbContext;
import javax.slee.UnrecognizedActivityException;
import javax.slee.facilities.Tracer;

import javax.slee.facilities.TimerEvent;
import javax.slee.facilities.TimerFacility;
import javax.slee.facilities.TimerID;
import javax.slee.facilities.TimerOptions;

import net.java.slee.resource.mgcp.JainMgcpProvider;
import net.java.slee.resource.mgcp.MgcpActivityContextInterfaceFactory;
import net.java.slee.resource.mgcp.MgcpConnectionActivity;
import net.java.slee.resource.mgcp.MgcpEndpointActivity;
import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import java.util.HashMap;
import java.util.Random;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Date;

import java.io.File;

import java.io.IOException;

import javax.slee.CreateException;
import javax.slee.ChildRelation;

//Weather Service - MTech Demo:
import za.co.csir.meraka.mobi4d.agnostic.sbb.weather_service.WeatherServiceEvent;
import za.co.csir.meraka.mobi4d.agnostic.sbb.weather_service.WeatherServiceResponse;
import za.co.csir.meraka.mobi4d.agnostic.sbb.weather_service.WeatherServiceRequest;

//Menu
import za.co.csir.meraka.mobi4d.resource.sbb.menu_service.MenuSbbLocalObject;
import za.co.csir.meraka.menu.Menu;

/**
 * With thanks to Original author of the Mobicents IVR Sbb example:
 *  Amit Bhayani
    @author Ishmael Makitla - CSIR Meraka Institute
 */

public abstract class IVRSbb implements Sbb {

	public final static String ENDPOINT_NAME = "/mobicents/media/IVR/$";

	public final static String JBOSS_BIND_ADDRESS = System.getProperty("jboss.bind.address", "127.0.0.1");

	private final static String MOBI4D_IVR = "2747";
		
	private static HashMap<Integer, String> provincesList;
	private static HashMap<Integer, String> ttsVoicesList;
	
	private static HashMap<String, String> cities;
	
	private static HashMap<String, String> localMenu;
	
	public final static String WELCOME = "http://" + JBOSS_BIND_ADDRESS + ":8080/mgcpdemo/audio/RQNT-ULAW.wav";
	public final static String _WELCOME = "http://" + JBOSS_BIND_ADDRESS + ":8080/mobi4d_ivr/audio/WELCOME.wav";
	
	private final static String MEDIA_PATH = "http://" + JBOSS_BIND_ADDRESS + ":8080/mobi4d_ivr/audio/";
	
	private static final String GET_CHILD_SBB_RELATION_TTS = "getTtsSbbLocalObject";
	private static final String GET_CHILD_SBB_RELATION_MENU = "getMenuSbbLocalObject";
	private static final String GET_CHILD_SBB_RELATION_USHAHIDI = "getUshahidiSbbLocalObject"; 
	
		
	private SbbContext sbbContext;
	private Context ctx;
	
	private NullActivityFactory nullActivityFactory;
	private NullActivityContextInterfaceFactory nullActivityContextInterfaceFactory;
	
	private TimerFacility timerFacility = null;

	// SIP
	private SleeSipProvider provider;

	private AddressFactory addressFactory;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;
	private SipActivityContextInterfaceFactory acif;

	// MGCP
	private JainMgcpProvider mgcpProvider;
	private MgcpActivityContextInterfaceFactory mgcpAcif;

	public static final int MGCP_PEER_PORT = 2427;
	public static final int MGCP_PORT = 2727;

	private Tracer logger;

	/** Creates a new instance of IVRSbb */
	public IVRSbb() {
	}

	//TODO: use intial-evel-select method...check there for any conditions to be met before creating an SBB instance
	//to handle the incoming call
	
	public void onCallCreated(RequestEvent evt, ActivityContextInterface aci) {
		Request request = evt.getRequest();

		FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
		ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);

		logger.info("Incoming call " + from + " " + to);
		String destination = to.toString();
		//check that the To address is mine, if not, drop the SIP call : all CSIR-hosted IVRs start with 2747_#
		if(destination.lastIndexOf(MOBI4D_IVR) < 0 ){ 
			logger.info("Incoming call from " + from + " To " + to+" Not my call, dropping");
			//send Bye?
			return;}
         
		String callee = destination.substring(destination.lastIndexOf(MOBI4D_IVR), destination.indexOf("@"));
		logger.info("Called Service "+callee+" Position of _ : "+callee.indexOf("_")+"  Callee length: "+callee.length());
        String menu="UNKNOWN-MENU";
        if(callee.indexOf("_")>0){
        menu = callee.substring(callee.indexOf("_")+1, callee.length());
         this.setNavigationCode(menu);
         
         this.setCallerId(from.toString());
         
         logger.info(" The call is intended for the Menu Service ID: "+menu +" Navigation Code already set to: "+this.getNavigationCode());
        } else{ logger.info(" The call is intended for the Menu Service ID: "+menu);}        
       
				
		// create Dialog and attach SBB to the Dialog Activity
		ActivityContextInterface daci = null;
		try {
			Dialog dialog = provider.getNewDialog(evt.getServerTransaction());
			dialog.terminateOnBye(true);
			daci = acif.getActivityContextInterface((DialogActivity) dialog);
			daci.attach(sbbContext.getSbbLocalObject());
		} catch (Exception e) {
			logger.severe("Error during dialog creation", e);
			respond(evt, Response.SERVER_INTERNAL_ERROR);
			return;
		}

		respond(evt, Response.RINGING);

		CallIdentifier callID = mgcpProvider.getUniqueCallIdentifier();
		this.setCallIdentifier(callID.toString());
		EndpointIdentifier endpointID = new EndpointIdentifier(ENDPOINT_NAME, JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);

		CreateConnection createConnection = new CreateConnection(this, callID, endpointID, ConnectionMode.SendRecv);

		try {
			String sdp = new String(evt.getRequest().getRawContent());
			createConnection.setRemoteConnectionDescriptor(new ConnectionDescriptor(sdp));
		} catch (ConflictingParameterException e) {
			// should never happen
		}

		int txID = mgcpProvider.getUniqueTransactionHandler();
		createConnection.setTransactionHandle(txID);

		MgcpConnectionActivity connectionActivity = null;
		try {
			connectionActivity = mgcpProvider.getConnectionActivity(txID, endpointID);
			ActivityContextInterface epnAci = mgcpAcif.getActivityContextInterface(connectionActivity);
			epnAci.attach(sbbContext.getSbbLocalObject());
		} catch (FactoryException ex) {
			ex.printStackTrace();
		} catch (NullPointerException ex) {
			ex.printStackTrace();
		} catch (UnrecognizedActivityException ex) {
			ex.printStackTrace();
		}

		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { createConnection });
	}

	public void onCreateConnectionResponse(CreateConnectionResponse event, ActivityContextInterface aci) {
		logger.info("Receive CRCX response: " + event.getTransactionHandle());

		ServerTransaction txn = getServerTransaction();
		Request request = txn.getRequest();

		ReturnCode status = event.getReturnCode();

		switch (status.getValue()) {
		case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:

			this.setEndpointName(event.getSpecificEndpointIdentifier().getLocalEndpointName());
			logger.info("***&& " + this.getEndpointName());

			ConnectionIdentifier connectionIdentifier = event.getConnectionIdentifier();

			this.setConnectionIdentifier(connectionIdentifier.toString());
			String sdp = event.getLocalConnectionDescriptor().toString();

			ContentTypeHeader contentType = null;
			try {
				contentType = headerFactory.createContentTypeHeader("application", "sdp");
			} catch (ParseException ex) {
			}

			String localAddress = provider.getListeningPoints()[0].getIPAddress();
			int localPort = provider.getListeningPoints()[0].getPort();

			Address contactAddress = null;
			try {
				contactAddress = addressFactory.createAddress("sip:" + localAddress + ":" + localPort);
			} catch (ParseException ex) {
			}
			ContactHeader contact = headerFactory.createContactHeader(contactAddress);
			
			//New TODO: fetch the first menu options -  welcome message from the Menu Service - make static call 
			sendFirstNotificationRequest();

			Response response = null;
			try {
				response = messageFactory.createResponse(Response.OK, request, contentType, sdp.getBytes());
			} catch (ParseException ex) {
				logger.severe("ParseException while trying to create OK Response", ex);
			}

			response.setHeader(contact);
			try {
				txn.sendResponse(response);
			} catch (InvalidArgumentException ex) {
				logger.severe("InvalidArgumentException while trying to send OK Response", ex);
			} catch (SipException ex) {
				logger.severe("SipException while trying to send OK Response", ex);
			}
			break;
		default:
			try {
				response = messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);
				txn.sendResponse(response);
			} catch (Exception ex) {
				logger.severe("Exception while trying to send SERVER_INTERNAL_ERROR Response", ex);
			}
		}
	}

	private void sendRQNT(String mediaPath, boolean createActivity){
		sendRQNT(mediaPath, createActivity, false);
	}
	private void sendRQNT(String mediaPath, boolean createActivity, boolean useTTS) {
		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(), JBOSS_BIND_ADDRESS + ":" + MGCP_PEER_PORT);

		NotificationRequest notificationRequest = new NotificationRequest(this, endpointID, mgcpProvider.getUniqueRequestIdentifier());
		
		ConnectionIdentifier connectionIdentifier = new ConnectionIdentifier(this.getConnectionIdentifier());
		
		if(useTTS){
			mediaPath = "ts("+mediaPath.replaceAll("\n","")+")";
		}
		
		EventName[] signalRequests = { new EventName(PackageName.Announcement, MgcpEvent.ann.withParm(mediaPath), connectionIdentifier) };
		notificationRequest.setSignalRequests(signalRequests);

		RequestedAction[] actions = new RequestedAction[] { RequestedAction.NotifyImmediately };
		
		RequestedEvent[] requestedEvents = {
				new RequestedEvent(new EventName(PackageName.Announcement, MgcpEvent.oc, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Announcement, MgcpEvent.of, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf0, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf1, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf2, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf3, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf4, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf5, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf6, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf7, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf8, connectionIdentifier), actions),

				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmf9, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmfA, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmfB, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmfC, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmfD, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmfStar, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Dtmf, MgcpEvent.dtmfHash, connectionIdentifier), actions) };
	    
		RequestedEvent[] requestedEventsShort = { new RequestedEvent(new EventName(PackageName.Announcement, MgcpEvent.oc, connectionIdentifier), actions),
				new RequestedEvent(new EventName(PackageName.Announcement, MgcpEvent.of, connectionIdentifier), actions)};
		
		if(this.getLastInputError()){			
			//disable all DTMF-events
			notificationRequest.setRequestedEvents(requestedEventsShort);
		} 
		else {notificationRequest.setRequestedEvents(requestedEvents);}	
		
		notificationRequest.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());

		NotifiedEntity notifiedEntity = new NotifiedEntity(JBOSS_BIND_ADDRESS, JBOSS_BIND_ADDRESS, MGCP_PORT);
		notificationRequest.setNotifiedEntity(notifiedEntity);

		if (createActivity) {
			MgcpEndpointActivity endpointActivity = null;
			try {
				endpointActivity = mgcpProvider.getEndpointActivity(endpointID);
				ActivityContextInterface epnAci = mgcpAcif.getActivityContextInterface(endpointActivity);
				epnAci.attach(sbbContext.getSbbLocalObject());
			} catch (FactoryException ex) {
				ex.printStackTrace();
			} catch (NullPointerException ex) {
				ex.printStackTrace();
			} catch (UnrecognizedActivityException ex) {
				ex.printStackTrace();
			}
		} // if (createActivity)

		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { notificationRequest });

		logger.info(" NotificationRequest sent");
	}

	public void onNotificationRequestResponse(NotificationRequestResponse event, ActivityContextInterface aci) {
		logger.info("onNotificationRequestResponse");

		ReturnCode status = event.getReturnCode();

		switch (status.getValue()) {
		case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:
			logger.info("The Announcement should have been started");
			break;
		default:
			ReturnCode rc = event.getReturnCode();
			logger.severe("RQNT failed. Value = " + rc.getValue() + " Comment = " + rc.getComment());

			// TODO : Send DLCX to MMS. Send BYE to UA
			break;
		}

	}
	
	/**
	 * When one of the requested events are observed, this method is invoked to process the observed event
	 * Since there's only one set of DTMF codes, I am reusing them using navigation code, for each navigation, the getSignal(String code) method 
	 * returns the media path for the signal that the media gatway is requested to play back.
	 */

	public void onNotifyRequest(Notify event, ActivityContextInterface aci) {
		
		logger.info("onNotifyRequest: Number of Voices Available ttsVoicesList.size() = "+this.ttsVoicesList.size());	
								
		logger.info("Previous Trail = "+this.getNavigationCode());
				
		NotifyResponse response = new  NotifyResponse(event.getSource(),ReturnCode.Transaction_Executed_Normally);
		response.setTransactionHandle(event.getTransactionHandle());

		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { response });

		EventName[] observedEvents = event.getObservedEvents();

		for (EventName observedEvent : observedEvents) {
			switch (observedEvent.getEventIdentifier().intValue()) {
			
			case MgcpEvent.REPORT_ON_COMPLETION:
				logger.info("Announcement Completed NTFY received");					
				break;
				
			case MgcpEvent.REPORT_FAILURE:
				logger.info("Announcemnet Failed received");
				// TODO : Send DLCX and Send BYE to UA
				break;
			case MgcpEvent.DTMF_0:

				logger.info("User has pressed 0");				
				this.setLastInput("0");
				doUserSelection(this.getNavigationCode(),this.getLastInput());	
				
				break;
								
			case MgcpEvent.DTMF_1:				

				logger.info("User has pressed 1");
				this.setLastInput("1");
				if(this.getAcceptKeyPress())
					doUserSelection(this.getNavigationCode(),this.getLastInput());				
				break;
				
			case MgcpEvent.DTMF_2:

				logger.info("User has pressed 2");
				this.setLastInput("2");
				doUserSelection(this.getNavigationCode(),this.getLastInput());				
				break;
				
			case MgcpEvent.DTMF_3:

				logger.info("User has pressed 3");
				this.setLastInput("3");
				doUserSelection(this.getNavigationCode(),this.getLastInput());				
				break;
				
			case MgcpEvent.DTMF_4:

				logger.info("User has pressed 4");
				this.setLastInput("4");
				doUserSelection(this.getNavigationCode(),this.getLastInput());				
				break;
				
			case MgcpEvent.DTMF_5:
				
				logger.info("User has pressed 5");
				this.setLastInput("5");
				doUserSelection(this.getNavigationCode(),this.getLastInput());				
				break;
				
			case MgcpEvent.DTMF_6:
				
				logger.info("User has pressed 6");
				this.setLastInput("6");
				doUserSelection(this.getNavigationCode(),this.getLastInput());				
				break;
				
			case MgcpEvent.DTMF_7:
				
				logger.info("User has pressed 7");
				this.setLastInput("7");
				doUserSelection(this.getNavigationCode(),this.getLastInput());				
				break;
				
			case MgcpEvent.DTMF_8:
				
				logger.info("User has pressed 8");
				this.setLastInput("8");
				doUserSelection(this.getNavigationCode(),this.getLastInput());				
				break;
				
			case MgcpEvent.DTMF_9:
				
				logger.info("User has pressed 9");						
				this.setLastInput("9");
				doUserSelection(this.getNavigationCode(),this.getLastInput());				
				break;
				
			case MgcpEvent.DTMF_A:
				logger.info("User has pressed A");
				this.setLastInput("A");
				doUserSelection(this.getNavigationCode(),this.getLastInput());	
				break;
			case MgcpEvent.DTMF_B:
				logger.info("User has pressed B");
				this.setLastInput("B");
				doUserSelection(this.getNavigationCode(),this.getLastInput());	
				break;
			case MgcpEvent.DTMF_C:
				logger.info("User has pressed C");
				this.setLastInput("C");
				doUserSelection(this.getNavigationCode(),this.getLastInput());	
				break;
			case MgcpEvent.DTMF_D:
				logger.info("User has pressed D");
				this.setLastInput("D");
				doUserSelection(this.getNavigationCode(),this.getLastInput());	

				break;
			case MgcpEvent.DTMF_STAR:
				logger.info("User has pressed *");
				this.setLastInput("*");
				doUserSelection(this.getNavigationCode(),this.getLastInput());	

				break;
			case MgcpEvent.DTMF_HASH:
				logger.info("User has pressed #");
				//This might be the user indicating that she has finished recording her voice message
				this.setLastInput("#");
				doUserSelection(this.getNavigationCode(),this.getLastInput());	
				break;
			}
		}
	}


	/**
	 * This event is invoked when a SIP-call party send a call-terminate message (BYE)
	 * This SBB response with an OK (200) message
	 */
	public void onCallTerminated(RequestEvent evt, ActivityContextInterface aci) {
		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndpointName(), JBOSS_BIND_ADDRESS + ":"+ MGCP_PEER_PORT);
		DeleteConnection deleteConnection = new DeleteConnection(this, endpointID);

		deleteConnection.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());
		mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });

		ServerTransaction tx = evt.getServerTransaction();
		Request request = evt.getRequest();

		try {
			Response response = messageFactory.createResponse(Response.OK, request);
			tx.sendResponse(response);
		} catch (Exception e) {
			logger.severe("Error while sending DLCX ", e);
		}
	}
	
		
	/**
	 * on Timer event.
	 * This method simply "stops" the recording - and then plays a "Dankie Siyabonga" announcement
	 * The assumption here is that the recording times out BEFORE the user presses Hash or hangs up.
	 */
	public void onTimerEvent(TimerEvent event, ActivityContextInterface aci) {		
		logger.info("****** Recorder TimerEvent received ******* ");
		
	}
	
	 /**
	  * MTech Demo: Event Handler for Weather-Request response event
	  */
	 public void onWeatherResponseEvent(WeatherServiceEvent event,ActivityContextInterface aci){
		 
		 if(event.getEventPayload() instanceof WeatherServiceResponse ){
			 
			 WeatherServiceResponse response = (WeatherServiceResponse)event.getEventPayload();
		     logger.info("Weather-Service-Response-Event received: \n Original-Request: "+response.getOriginalRequest().toString()+" \n Response-Data: "+response.getResponse());
		     //build a reply to send back to the user:
		     sendRQNT(response.getResponse(),false,true); 			 
		 }	 	 
	 }

//----------------------------------------------------------------------------------------
	private void respond(RequestEvent evt, int cause) {
		Request request = evt.getRequest();
		ServerTransaction tx = evt.getServerTransaction();
		try {
			Response response = messageFactory.createResponse(cause, request);
			tx.sendResponse(response);
		} catch (Exception e) {
			logger.warning("Unexpected error: ", e);
		}
	}

	private ServerTransaction getServerTransaction() {
		ActivityContextInterface[] activities = sbbContext.getActivities();
		for (ActivityContextInterface activity : activities) {
			if (activity.getActivity() instanceof ServerTransaction) {
				return (ServerTransaction) activity.getActivity();
			}
		}
		return null;
	}

	public void setSbbContext(SbbContext sbbContext) {
		
		this.sbbContext = sbbContext;
		this.logger = sbbContext.getTracer(IVRSbb.class.getSimpleName());

		try {
			
			provincesList   = new HashMap<Integer, String>();
			ttsVoicesList   = new HashMap<Integer, String>();
						
			//Locations-workarounds
			cities          = new HashMap<String, String>();
			
			//local menu - workaround
			localMenu = new HashMap<String, String>();
			
			ctx = (Context) new InitialContext().lookup("java:comp/env");
			
			//Null-Activity-Factor
			nullActivityFactory = (NullActivityFactory)ctx.lookup("slee/nullactivity/factory");
			nullActivityContextInterfaceFactory = (NullActivityContextInterfaceFactory) ctx.lookup("slee/nullactivity/activitycontextinterfacefactory");

			// initialize SIP API
			provider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");

			addressFactory = provider.getAddressFactory();
			headerFactory = provider.getHeaderFactory();
			messageFactory = provider.getMessageFactory();
			acif = (SipActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainsip/1.2/acifactory");

			// initialize media api
			mgcpProvider = (JainMgcpProvider) ctx.lookup("slee/resources/jainmgcp/2.0/provider/demo");
			mgcpAcif = (MgcpActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainmgcp/2.0/acifactory/demo");
			
			//geting the timer facility
			timerFacility = (TimerFacility) ctx.lookup("slee/facilities/timer");

		} catch (Exception ne) {
			logger.severe("Could not set SBB context:", ne);
		}
	}
	
		
	public void onServiceStarted(javax.slee.serviceactivity.ServiceStartedEvent event, ActivityContextInterface aci) {
		
		logger.info("onServiceStartedEvent(" + event.toString() + ", " + aci.toString() + ")"); 
				
		try {
			// Get this SBB's service activity and test if this service is starting
			ServiceActivity sa = ((ServiceActivityFactory) ctx.lookup("slee/serviceactivity/factory")).getActivity();
			
			if (sa.equals(aci.getActivity())) { 
				
				logger.info("===========================================================");
				logger.info("||          MOBI4D IVR DEMO SERVICE STARTED               ||");
				logger.info("===========================================================");
				//TODO: Sanity check...
								
				//load some dummy location data for demo
				initializeDummyLocations();
				
			}		
			// Detach this SBB's service activity to stop receiving further events on this activity
			aci.detach(this.sbbContext.getSbbLocalObject());
		}
		catch (Exception e) { logger.severe("onServiceStartedEvent(...): " + e.toString(), e); }
		
	}
	
					/**
				 	  * Mobi4D Specific helper functions
				 	 */
		
		
	/**
	 * This method simply creates and returns unique-ish filename
	 */
	private String getUniqueFilename(){
		
		String filename ="";
		long num = new Random().nextLong()^System.currentTimeMillis();
        filename = String.valueOf(num);
        filename = filename.replace("-", "_");
        
		return filename;
	}
	
	/**
	 * This method simply loads names of provinces in the list
	 */
		
	
	/**
	 * This map was used for lab testing only - Locations: X,Y
	 */
	
	private void initializeDummyLocations(){
	//todo
		
	}
	 
	
	/**
	 * This method is called once each time a new caller comes online
	 * It checks if the selected IVR service exists else it picks the pre-configured IVR service
	 */
	private void sendFirstNotificationRequest(){		
		
		String userOption =  (this.getLastInput() ==null? "":this.getLastInput());
		String defaultMenu ="No-MENU";
		//get default menu
		if(this.getNavigationCode() == null){
			try{
				defaultMenu = (String)ctx.lookup("defaultMenu");
			}
			catch(NamingException ne){logger.severe(" Error looking up for default Menu environmental entry", ne);}
		}
		else{ defaultMenu = this.getNavigationCode();} 
		this.setAcceptKeyPress(true);
						
		//play the menu-specific announcement	
		doUserSelection(defaultMenu,userOption);
	}
	
	/**
	 * This method is responsible for fetching the menu items from the menu
	 * service. It receives the user's DTMF choice and updates the index and queries the menu service
	 * for the next set of options according to the user's selection.
	 * 
	 * For the menu items received, it extracts the menu attachment which points to a media file, this then instructs
	 * the media server to play the referred file.
	 */ 
	private void doUserSelection(String currentMenuIndex, String dtmfKey){	
		
		//TODO: fire an event to BPU to handle this user selection
				
		//Work-around for the MGCP DTMF delays
		//Somehow when the user presses a key, the system tends to treat this as two keypresses - this is not correct!
		//TODO: Specify DTMF delay if possible
		logger.info("Can accept this User-Selection ? "+String.valueOf(this.getAcceptKeyPress()));
		if(!this.getAcceptKeyPress()) {
			return;
		 } else{this.setAcceptKeyPress(false);}
				
		 //get user's language choice
		    String voiceName =  dtmfKey;
		    boolean createActivity = false;
		   
			if(this.getMenuSbbLocalObjectInterface() == null){ createSbbChildRelation(GET_CHILD_SBB_RELATION_MENU); }
			if (this.getMenuSbbLocalObjectInterface() !=null){
				   String defaulMenuError = "You have supplied a wrong selection, no options found for your selection. Please listen carefully to the options again before making a selection";
				   Menu menuResult;

				   menuResult = this.getMenuSbbLocalObjectInterface().find("","","",defaulMenuError, currentMenuIndex, dtmfKey,"English"); 
					
				   //No menu?
				   if(menuResult.getType() == Menu.Type.Null){
					   logger.info("Menu Result -Type = NUll : ERROR "); 
					 //ask the  Media Server to play the  defaulMenuError
					 //TODO: disable all keys					   
					   this.setLastInputError(true);
					   sendRQNT( defaulMenuError, createActivity, true);
					   this.setAcceptKeyPress(true);
					   return;
				   }
				  		
				   logger.info("Menu Result : \n"+menuResult.toString());
				   
				    //get and store the trail				   
				    this.setNavigationCode(menuResult.getTrail());				    
				    
				     if(menuResult.getType() == Menu.Type.Menu){
				    	 logger.info("Menu Result -Type = Menu : "); 
				    	//collecting user activities - will find a better use of the collection information later on
						//lot of if's and else's to determine what the user has or has not done						   
				    	 updateCallInfo(menuResult.getTrail(),dtmfKey);
				    	
						 //the formatted (verbose) menu will be translated to audio using Mobicents Media Server's tts engine!
						 String text = formatMenuOptions(menuResult);	
						 String title = menuResult.getTitle()+". \n";
						 sendRQNT(title+text, createActivity, true);							 
							
				     }
				     else if (menuResult.getType() == Menu.Type.Command){
				    	 logger.info("Menu Result - Type = Command : Running-in-Demo Mode "); 
				    	 //whatever the user clicked, take it to be a location	
				    	 updateCallInfo(menuResult.getTrail(),dtmfKey);				    	 				    	 
				     }
				     else if (menuResult.getType() == Menu.Type.Static){
				    	 logger.info("Menu Result -Type = Static : Text"); 
				    	 updateCallInfo(menuResult.getTrail(),dtmfKey);
				    	 //static menu-tuype indicates the end of navigation - so the end of call!
						 doLogCall(true);
					} 
				 } //end-if Menu !=null
					
		createActivity = false;
		logger.info("Is the next call also the first call to Media Server? : "+String.valueOf(createActivity));
				   								
	 } //end of Menu SBB use case 
	
	/**
	 * This method is intended to build call record as the user navigates through the system
	 * It assumes the IVR-service knows how the menu looks like. Ideally, the menu should provide
	 * the IVR service with details such as user-selection-values (e.g crime if user selected 1.)
	 * The menu, if it hosts the intelligence, must also indicate current mode (navigation,input,etc)
	 * The method maintains the user selection history in the form trail#trail#...
	 */
	private void updateCallInfo(String trail, String key){
		//check if dummy data has been loaded
		 if(this.localMenu.size() <=0) {
			 	logger.info("No Pre-loaded data...loading dummy data now...");
			 	initializeDummyLocations();
			 	logger.info("Pre-loaded data items = "+this.localMenu.size());
			} 
		 else{
			   logger.info("Number of pre-loaded Menu Data = "+this.localMenu.size()); 
		     }
		
		String userSelectionRecord;
		 if(this.localMenu.get(trail)==null){
			 logger.info(trail+" Is not a Pre-loaded trail value: "+this.localMenu.get(trail)); 
		 } else {			 
			 logger.info("Found Corresponding Pre-loaded Value: "+this.localMenu.get(trail));
			 //log to callHistory - separate by #
			 if(this.getCallHistory()!=null){ 
				 userSelectionRecord = this.getCallHistory()+"#"+trail;
				 this.setCallHistory(userSelectionRecord);
			  }
			 else{ 
				 this.setCallHistory(trail); 
			 }			 
		 }		 
		//did the user select incident category check category CMP
		 logger.info("New call Record: "+this.getCallHistory()); 		 
		 this.setAcceptKeyPress(true);
   }
	
	/**
	 * This method is used to log a call that just ended. In some cases it may involve asynchronous invocation of other
	 * SBBs. In this case, the log call for MTech Demo sends a weather request and awaits reponse.
	 * The response event handler will play the weather response as an announcement!
	 *
	 */
	private boolean doLogCall(boolean event){
		boolean logged = false;
		
		String callDescription;
		
		//TODO: Loop through the callHistory and fetch values from the map:
		String UserSelection = this.getCallHistory();
		
		logger.info("Call History: "+UserSelection);
		if(this.getCallHistory() == null){ logger.info("CALL HISTORY NOT FOUND - CANNOT LOG CALL"); return false;}
		
		//deduce user selected values from the call-history map! Province and a 
		//City in that province for which the weather is requested
		
		String userOptions[] = UserSelection.split("#");;
		String _province = this.localMenu.get(userOptions[0]);
		String _city     = this.localMenu.get(userOptions[1]);
		
		logger.info("Province Option  :"+_province);
		logger.info("City Option      :"+_city);
		
		callDescription = "Caller: "+this.getCallerId()+", Location: City name: "+_city+" in "+_province+" province";
								
		//Caller		
		sendWeatherRequest(this.getCallerId(),_city);
						
		return logged;		
	}
	
	/**
	 * This method is used to play the Thank you Message to the user before sending the complaint to Ushahidi
	 */
	private void playThankYou(){		
		String mediaPath = "http://"+JBOSS_BIND_ADDRESS+":8080/audioFiles/thankyou.wav";
		logger.info("About to play Thank-You message from: "+mediaPath);
		sendRQNT(mediaPath, false);
	}
	/**
	 * This method is used to instruct the Media Gateway to start recording the voice message.
	 * It generates unique filename for the voice message.
	 * Usage Example: The Menu Sbb may come back with a Command answer instructing the IVR Sbb to start recording, the IVR SBB will invoke
	 * this method and will set the timer!
	 */
	
	private void doVoiceRecording(Notify event,ActivityContextInterface callACI){
				
		    NotificationRequest notificationRequest = new NotificationRequest(this,event.getEndpointIdentifier(), mgcpProvider.getUniqueRequestIdentifier());

			NotifiedEntity notifiedEntity = new NotifiedEntity(JBOSS_BIND_ADDRESS, JBOSS_BIND_ADDRESS,MGCP_PORT);
			notificationRequest.setNotifiedEntity(notifiedEntity);
			notificationRequest.setTransactionHandle(mgcpProvider.getUniqueTransactionHandler());

			ConnectionIdentifier connId = new ConnectionIdentifier(this.getConnectionIdentifier());
           
			//Asking the Media Gateway to play record (pr) using the AUPackage.AU package
			//upon acting on this signal request, the MG will start recording what the user is saying and
			///saving the resulting file to the specified path
			
			//signal to play record
			EventName[] signalRequests = { new EventName(AUPackage.AU, AUMgcpEvent.aupr.withParm(getUniqueFilename()+".wav"), connId) };
			notificationRequest.setSignalRequests(signalRequests);

			mgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { notificationRequest });
			setTimer(callACI);
		
	}
	
	/**
	 * This method is used to format the Menu Options in a manner that the TTS service
	 * can convert to speech properly. It replaces the character 1 with string "one" for instance.
	 * It returns the verbose version of the menu options
	 */
	
	private String formatMenuOptions(Menu _menu){
		
		String verboseText ="";
		List<String> menuOptions = _menu.getOptions();
		
		HashMap<String, String> menuKeysMap = new HashMap<String,String>();
        menuKeysMap.put("0", "zero");
        menuKeysMap.put("1", "one");
        menuKeysMap.put("2", "two");
        menuKeysMap.put("3", "three");
        menuKeysMap.put("4", "four");
        menuKeysMap.put("5", "five");
        menuKeysMap.put("6", "six");
        menuKeysMap.put("7", "seven");
        menuKeysMap.put("8", "eight");
        menuKeysMap.put("9", "nine");
        menuKeysMap.put("10", "A");
        menuKeysMap.put("11", "Bee");
        menuKeysMap.put("*", "Star");
        menuKeysMap.put("**", "Double Star");
        menuKeysMap.put("#", "Hash");
        menuKeysMap.put("##", "double hash");
        
		if(menuOptions !=null ){
			Iterator<String> optionsIterator = menuOptions.iterator();		
			int x=1;		
			while(optionsIterator.hasNext()){
			
				String option = optionsIterator.next();	
				option = (x)+"."+option;
			
				String optionTitle = option.substring(option.indexOf(".")+1);
				String optionKey = option.substring(0, option.indexOf("."));
				optionKey = menuKeysMap.get(optionKey);
	        	       						
				verboseText =verboseText+"For "+optionTitle+" press "+optionKey+"."; 
				x++;
			}
	     }//end if options were available	
		else if (_menu.getType() == Menu.Type.Static){			
			verboseText = 	_menu.getText()+".";		
		}
		//now append the back and home options
		
		if(_menu.getBack() != null){
			verboseText += "For "+_menu.getBack()+" press "+ menuKeysMap.get(_menu.getBackPrefix())+".";	
		}
		if(_menu.getHome() != null){
			verboseText += "For "+_menu.getHome()+" press "+ menuKeysMap.get(_menu.getHomePrefix())+".";	
		}
						
		logger.info("Verbose Menu Options: \n"+verboseText);
		
		return verboseText;
	}
	
	/**
	 * This method is used to create filename based on the provided menu-trail
	 * It returns the name of the file.
	 * Example: Trail = 1.2.3[4.1.2].4.3
	 * Example filename = 1.2.3_4.1.2_.4.3.wav
	 */
	private String createFileNameFromTrail(String menuTrail){
		String filename="";
		
		filename = menuTrail.replace("[","_");
		filename = filename.replace("]","_");
		filename+=".wav";
		
		return filename;
	}
	
				
	 /**
	  * This method is used to fire an access-agnostic event from within the SLEE component model (SBBs)
	  * to yet other SBBs within SLEE. It's a private communication channels for SBBs, and minimizes the
	  * amount of knowledge each SBB must have of others - they only either fire or receive events of THE SAME EVENT-TYPE.
	  */

	 private void sendWeatherRequest(String requester, String weatherPlace){
		// Create a null_activity that can be used by the SBB to create an event channel and to manage its lifecycle independent from other SLEE or RA activities
		 logger.info("sendWeatherRequest( "+weatherPlace+" )");
		 NullActivity nullActivity = nullActivityFactory.createNullActivity();
			
		// Create and set a null_activity_context_interface_factory that can be used by the SBB to obtain an activity_context_interface object for a null activity		
		ActivityContextInterface nullActivityContextInterface = nullActivityContextInterfaceFactory.getActivityContextInterface(nullActivity);
		
		nullActivityContextInterface.attach(this.sbbContext.getSbbLocalObject());
		
		Object requestSender = (Object)requester;
		WeatherServiceEvent weatherServiceRequestEvent = new WeatherServiceEvent(new WeatherServiceRequest(requestSender,weatherPlace));
		
		logger.info("Sending Weather Service Request: "+weatherServiceRequestEvent); 
		
		this.fireWeatherRequestEvent(weatherServiceRequestEvent, nullActivityContextInterface, null);	  
		 
	 }
	 
			
	/**
	 * This method is used to create the child Sbb relations and to set appropriate CMP fields
	 * to hold references to the child Sbbs
	 */
	
	private void createSbbChildRelation(String childRelationMethodName){
		
		if (childRelationMethodName.equalsIgnoreCase(GET_CHILD_SBB_RELATION_MENU)){
			ChildRelation menuChildRelation = getMenuSbbChildRelation();
			
			try{
				MenuSbbLocalObject menuChildSbb = (MenuSbbLocalObject)menuChildRelation.create();			   
			    this.setMenuSbbLocalObjectInterface(menuChildSbb);
			  }
			catch (CreateException ce){ 
				if (logger.isSevereEnabled())
					logger.severe("Could not create Menu Sbb Child Relation", ce);				
			 }			
		} 
		else
		{ if(logger.isWarningEnabled()){ logger.warning("Unknown Child-Relation-Method-Name :"+childRelationMethodName);} }
	
	}
	
	
	/**
	 * This method starts the timer on the provided ACI
	 */
	
	private void setTimer(ActivityContextInterface ac) {
		TimerOptions options = new TimerOptions();
		//options.setPersistent(true); //Deprecated

		// Set the timer on ACI
		TimerID timerID = this.timerFacility.setTimer(ac, null, System.currentTimeMillis() + 30000, options);

		this.setTimerID(timerID);
	}
	
	/**
     * Section 5.6 of The JSLEE 1.1 Specification
     * Implementation of the methods defined in the local interface
   */

   /**
     * This method is invoked synchronously (by one of Mobi4D SBBs) through the SBB's local interface to
     * make/create a SIP call
     * Returns True if the SIP Signalling was successful else returns false if there was a problem with call setup
   */
public boolean makeSipCall(String caller, String callee){
	
	if (logger.isInfoEnabled())
		logger.info("Make-Sip-Call [Inside IVR-SBB] (Caller: "+caller+" - Callee:"+callee);
	
	return true;
}

	
	public abstract String getConnectionIdentifier();

	public abstract void setConnectionIdentifier(String connectionIdentifier);

	public abstract String getCallIdentifier();

	public abstract void setCallIdentifier(String CallIdentifier);

	public abstract String getRemoteSdp();

	public abstract void setRemoteSdp(String remoteSdp);

	public abstract String getEndpointName();

	public abstract void setEndpointName(String endpointName);
	
	/**
	 * Mobi4D-specific CMP fields 
	 */
	
	public abstract void setCallHistory(String newTrail);
	public abstract String getCallHistory();
		
	public abstract void setNavigationCode(String code);
	public abstract String getNavigationCode();
	
	public abstract String getLastInput();
	public abstract void setLastInput(String userSelection);
	
	public abstract void setLastInputError(boolean true_false);
	public abstract boolean getLastInputError();	
	
	//Work-Around CMP to check if the SBB is allowed to accept user input (DTMFs)
	public abstract void setAcceptKeyPress(boolean yes_no);
	public abstract boolean getAcceptKeyPress();
	
	public abstract String getCallerId();
	public abstract void setCallerId(String callerId);	
	
	// 'timerID' CMP field setter
	public abstract void setTimerID(TimerID value);

	// 'timerID' CMP field getter
	public abstract TimerID getTimerID();
	
	//bye CMP Field, used to signal end-of-recording
	public abstract void setBye(boolean value);
	public abstract boolean getBye();
		
	public abstract ActivityContextInterface getNullActivity();
	public abstract void setNullActivity(ActivityContextInterface nullAci);
	
	//Events -fire methods
	public abstract void fireWeatherRequestEvent(WeatherServiceEvent weatherEvent, ActivityContextInterface aci, javax.slee.Address address);
	
	 //--------------------------------Region: CMP Binding -and- ChildRelation Methods ----------------------------
	 /**
		 * This property is used when calling SYNCHRONOUS FUNCTIONS on other SBBs.
		 * This is a property getter example of a child-relation that was defined in "sip-service/sbb/src/main/resources/META-INF/sbb-jar.xml" as a child-relation-method.
		 * 
	  * @return Returns the child-relation
	  */	
		
		public abstract ChildRelation getMenuSbbChildRelation();
		
		//CMP Bindings
		
		//abstract methods for the child-relations's CMP getters and setters
	
		public abstract void setMenuSbbLocalObjectInterface(MenuSbbLocalObject menuLocalInterface);
		public abstract MenuSbbLocalObject getMenuSbbLocalObjectInterface();		

	public void unsetSbbContext() {
		this.sbbContext = null;
		this.logger = null;
	}

	public void sbbCreate() throws CreateException {
	}

	public void sbbPostCreate() throws CreateException {
	}

	public void sbbActivate() {
	}

	public void sbbPassivate() {
	}

	public void sbbLoad() {
	}

	public void sbbStore() {
	}

	public void sbbRemove() {
	}

	public void sbbExceptionThrown(Exception exception, Object object, ActivityContextInterface activityContextInterface) {
	}

	public void sbbRolledBack(RolledBackContext rolledBackContext) {
	}
}
