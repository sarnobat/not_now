package com.google.api.services.samples.calendar.cmdline;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

public class ListDisplaySynchronous {
	static final String string = "/Users/sarnobat/.gcal_task_warrior";
	static final File file = new File(string + "/tasks.json");

	public static void main(String[] args) throws NoSuchProviderException,
			MessagingException, IOException {

		getErrands();

		System.out.println("List updated");
	}

	private static void getErrands() throws NoSuchProviderException,
			MessagingException, IOException {
		Message[] msgs = getMessages();
		System.out.println("Messages obtained");

		JSONObject json = createsJson(msgs);
		FileUtils.writeStringToFile(file, json.toString());
	}

	private static JSONObject createsJson(Message[] msgs)
			throws MessagingException {
		int i = 0;
		JSONObject json = new JSONObject();
		for (Message aMessage : msgs) {
			i++;
			String title = aMessage.getSubject().split("@")[0].replace(
					"Reminder: ", "");
			System.out.println(i + "\t" + title);
			JSONObject jsonValue = new JSONObject();
			jsonValue.put("title", title);
			json.put(Integer.toString(i), jsonValue);
		}
		return json;
	}

	private static Message[] getMessages() throws NoSuchProviderException,
			MessagingException {
		System.out.println("Connecting");
		Store theImapClient = connect();
		Folder folder = theImapClient
				.getFolder("3 - Urg - time sensitive - this week");
		System.out.println("Opening");
		folder.open(Folder.READ_ONLY);

		System.out.println("Getting Message list");
		Message[] msgs = folder.getMessages();

		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		System.out.print("Fetching attributes...");
		folder.fetch(msgs, fp);
		System.out.println("done");
		return msgs;
	}

	private static Store connect() throws NoSuchProviderException,
			MessagingException {
		Properties props = System.getProperties();
		String password = "varelA77";// System.getenv("GMAIL_PASSWORD");
		if (password == null) {
			throw new RuntimeException(
					"Please specify your password by running export GMAIL_PASSWORD=mypassword groovy mail.groovy");
		}
		props.setProperty("mail.store.protocol", "imap");
		Store theImapClient = Session.getInstance(props).getStore("imaps");
		theImapClient.connect("imap.gmail.com", "sarnobat.hotmail@gmail.com",
				password);
		return theImapClient;
	}
}