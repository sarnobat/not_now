import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.mail.BodyPart;
import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.Path;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class GetEventsFromEmail {

	private static final String CONFIG_FOLDER = home() + "/.gcal_task_warrior";

	private static String home() {
		return System.getProperty("user.home");
	}

	private static final String TASKS_FILE = CONFIG_FOLDER + "/tasks.json";
	private static final String CLIENT_SECRETS = home() + "/github/google_task_warrior/client_secrets.json";

	private static String formatTitleForPrinting(String string) {
		String[] aTitle = string.split("@");
		String repeating = "";
		if (aTitle.length > 1 && aTitle[1].contains("Repeating")) {
			repeating = "[Repeating] ";
		}
		String aTitleMain = aTitle[0].replace("Reminder: ", "").replace("Notification: ", "");
		String printedTitle = repeating + aTitleMain;

		String capitalize = StringUtils.capitalize(printedTitle);
		return capitalize;
	}

	/************************************************************************
	 * Boilerplate
	 ************************************************************************/

	private static Calendar getCalendarService(String CLIENT_SECRETS) {
		return getCalendarService(FileUtils.getFile(CLIENT_SECRETS), home()
				+ "/.store/calendar_sample");
	}

	private static Calendar getCalendarService(HttpTransport httpTransport, File file2,
			String pathname) throws IOException, FileNotFoundException {
		return checkNotNull(getClient(httpTransport, new InputStreamReader(new FileInputStream(
				file2)), new File(pathname)));
	}

	private static Calendar getClient(HttpTransport httpTransport, InputStreamReader clientSecrets,
			java.io.File homeDir) throws IOException {
		return new Calendar.Builder(httpTransport, JacksonFactory.getDefaultInstance(),
				getAuthCode(httpTransport, clientSecrets, homeDir).authorize("user"))
				.setApplicationName("gcal-task-warrior").build();
	}

	private static AuthorizationCodeInstalledApp getAuthCode(HttpTransport httpTransport,
			InputStreamReader clientSecrets, java.io.File homeDir) throws IOException {
		return new AuthorizationCodeInstalledApp(new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, JacksonFactory.getDefaultInstance(), GoogleClientSecrets.load(
						JacksonFactory.getDefaultInstance(), clientSecrets), ImmutableSet.of(
						CalendarScopes.CALENDAR, CalendarScopes.CALENDAR_READONLY))
				.setDataStoreFactory(new FileDataStoreFactory(homeDir)).build(),
				new LocalServerReceiver());
	}

	/************************************************************************
	 * Boilerplate
	 ************************************************************************/

	@Deprecated
	// Why?
	static void getErrands(String tasksFilePath) throws NoSuchProviderException,
			MessagingException, IOException {
		JSONObject json = new JSONObject();
		json.put("tasks", getErrandsJsonFromEmail(tasksFilePath));
		System.out.println("GetEventsFromEmail.ListDisplaySynchronous.getErrands()"
				+ json.toString(2));
	}

	static JSONObject getErrandsJsonFromEmail(String tasksFilePath) throws NoSuchProviderException,
			MessagingException, IOException {
		System.out.println("getErrandsJsonFromEmail() - " + "Messages obtained");
		JSONObject json = createJsonListOfEvents(getMessages());
		json.put("daysToPostpone", getPostponeCount(tasksFilePath));
		return json;
	}

	private static int getPostponeCount(String tasksFilePath) throws IOException {
		int DAYS_TO_POSTPONE = 30;
		int daysToPostponeSaved = DAYS_TO_POSTPONE;
		if (!new File(tasksFilePath).exists()) {
			daysToPostponeSaved = DAYS_TO_POSTPONE;
		} else {
			String errands = FileUtils.readFileToString(new File(tasksFilePath));
			JSONObject allErrandsJsonOriginal = new JSONObject(errands);
			if (allErrandsJsonOriginal.has("daysToPostpone")) {
				daysToPostponeSaved = allErrandsJsonOriginal.getInt("daysToPostpone");
			} else {
				daysToPostponeSaved = DAYS_TO_POSTPONE;
			}
		}
		return daysToPostponeSaved;
	}

	private static JSONObject createJsonListOfEvents(Message[] msgs) throws MessagingException {
		Map<String, JSONObject> messages = new TreeMap<String, JSONObject>();
		for (Message aMessage : msgs) {
			JSONObject messageMetadata = Preconditions.checkNotNull(getMessageMetadata(aMessage));
			String string = messageMetadata.getString("title");
			String capitalize = formatTitleForPrinting(string);
			messages.put(capitalize, messageMetadata);
		}
		int i = 0;
		JSONObject jsonToBeSaved = new JSONObject();
		for (String aTitle : new TreeSet<String>(messages.keySet())) {
			++i;
			JSONObject messageMetadata = messages.get(aTitle);
			jsonToBeSaved.put(Integer.toString(i), messageMetadata);
		}
		return jsonToBeSaved;
	}

	private static JSONObject getMessageMetadata(Message aMessage) {
		JSONObject errandJsonObject;
		try {
			errandJsonObject = new JSONObject();
			String title;
			// Leave this as-s for writing. Only when displaying should you
			// abbreviate
			title = aMessage.getSubject();

			errandJsonObject.put("title", title);
			return errandJsonObject;
		} catch (MessagingException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static Message[] getMessages() throws NoSuchProviderException, MessagingException {
		// System.out.println("Connecting");
		Store theImapClient = connect();
		Folder folder = theImapClient.getFolder("3 - Urg - time sensitive");
		// System.out.println("Opening");
		folder.open(Folder.READ_ONLY);

		// System.out.println("Getting Message list");
		Message[] msgs = folder.getMessages();

		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		// System.out.print("getMessages() - Fetching message attributes...");
		folder.fetch(msgs, fp);
		// System.out.println("done");
		
		for (Message aMessage : msgs) {
			JSONObject messageMetadata = Preconditions.checkNotNull(getMessageMetadata(aMessage));
			String string = messageMetadata.getString("title");
			String capitalize = formatTitleForPrinting(string);
			MimeMultipart content;
			try {
				content = (MimeMultipart) aMessage.getContent();
				Object bodyPart = content.getBodyPart(0).getContent();
				System.out.println("GetEventsFromEmail.createJsonListOfEvents() " + bodyPart);
				System.out.println("GetEventsFromEmail.createJsonListOfEvents() " + content.getBodyPart(1));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		theImapClient.close();
		return msgs;
	}

	private static Store connect() throws NoSuchProviderException, MessagingException {
		Properties props = System.getProperties();
		String password = System.getenv("GMAIL_PASSWORD");
		if (password == null) {
			throw new RuntimeException(
					"Please specify your password by running export GMAIL_PASSWORD=mypassword groovy mail.groovy");
		}
		props.setProperty("mail.store.protocol", "imap");
		Store theImapClient = Session.getInstance(props).getStore("imaps");
		theImapClient.connect("imap.gmail.com", "sarnobat.hotmail@gmail.com", password);
		return theImapClient;
	}

	private static void getErrandsInSeparateThread() {
		try {
			getErrands(TASKS_FILE);
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws URISyntaxException, NoSuchProviderException,
			MessagingException, IOException {
		if (!Paths.get(CLIENT_SECRETS).toFile().exists()) {
			throw new RuntimeException("Make sure ~/client_secrets.json exists");
		}
		getErrandsInSeparateThread();
	}

}
