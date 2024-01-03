// Import Java Utilities
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import com.nylas.models.Thread;
import com.nylas.resources.Contacts;
import com.nylas.resources.Messages;
import java.nio.file.Files;

// Import Spark and Handlebars libraries
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import spark.ModelAndView;
import static spark.Spark.*;
import spark.template.handlebars.HandlebarsTemplateEngine;

//Import Nylas Packages
import com.nylas.NylasClient;
import com.nylas.models.*;

//Import DotEnv to handle .env files
import io.github.cdimascio.dotenv.Dotenv;

public class EmailThreading {

    static ListResponse<Contact> get_contact(String grant_id, NylasClient nylas, String email) throws NylasSdkTimeoutError, NylasApiError {
    ListContactsQueryParams query_params = new ListContactsQueryParams.
            Builder().
            email(email).
            build();
        return nylas.contacts().list(grant_id, query_params);
    }

    static void download_contact_picture(String grant_id, NylasClient nylas, String id) throws NylasSdkTimeoutError, NylasApiError{
        Contact contact = nylas.contacts().find(grant_id, id).getData();
        String img = "src/main/resources/public/images/" + contact.getGivenName() + "_" + contact.getSurname() + ".png";
        try {
            assert contact.getPictureUrl() != null;
            Path target = Path.of(img);
            Files.deleteIfExists(target);
            try(InputStream in = new URL(contact.getPictureUrl()).openStream()){
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws
            NylasSdkTimeoutError, NylasApiError
    {
        main_thread mainThread = new main_thread();
        ArrayList<main_thread> data_threads = new ArrayList<>();
        ArrayList<ArrayList<main_thread>> all_threads = new ArrayList<>();

        staticFiles.location("/public");
        // Load the .env file
        Dotenv dotenv = Dotenv.load();
        // Connect it to Nylas using the Access Token from the .env file
        NylasClient nylas = new NylasClient.Builder(dotenv.get("V3_TOKEN_API")).build();
        // Get access to messages
        Messages messages = nylas.messages();
        // Get access to contacts
        Contacts contacts = nylas.contacts();
        // Default path when we load our web application

        // Hashmap to send parameters to our handlebars view
        Map<String, String> map = new HashMap<String, String>();
        map.put("search", "");

        get("/", (request, response) ->
                        // Create a model to pass information to the handlebars template
                        // Call the handlebars template
                        new ModelAndView(map, "main.hbs"),
                new HandlebarsTemplateEngine());

        // When we submit the form, we're posting data
        post("/", (request, response) -> {
            all_threads.clear();
            // Get parameter from form
            String search = request.queryParams("search");
            // Search all threads related to the email address

            ListThreadsQueryParams queryParams = new ListThreadsQueryParams.Builder().
                    searchQueryNative("from: " + search).
                    build();

            ListResponse<Thread> thread = nylas.threads().list(dotenv.get("GRANT_ID"), queryParams);

            if (search.isEmpty()) {
                String halt_msg = "<html>\n" +
                        "<head>\n" +
                        "    <script src=\"https://cdn.tailwindcss.com\"></script>\n" +
                        "    <title>Nylas' Email Threading</title>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "<div class=\"bg-red-300 border-green-600 border-b p-4 m-4 rounded w-2/5 grid place-items-center\">\n" +
                        "<p class=\"font-semibold\">You must specify all fields</p>\n" +
                        "</div>\n" +
                        "</body>\n" +
                        "</html>";
                halt(halt_msg);
            }

            String body = "";
            String names = "";
            String pictures = "";

            // Look for threads with more than 1 message
            for (Thread msg_thread : thread.getData()) {

                // Only add threads with two messages or more
                assert msg_thread.getMessageIds() != null;
                if (msg_thread.getMessageIds().size() > 1) {
                    // Get the subject of the first email
                    String subject = msg_thread.getSubject();
                    // Loop through all messages contained in the thread
                    for (String message_ids : msg_thread.getMessageIds()) {
                        // Get information from the message
                        Response<Message> message = nylas.messages().find(dotenv.get("GRANT_ID"), message_ids);
                        // Try to get the contact information
                        ListResponse<Contact> contact = get_contact(dotenv.get("GRANT_ID"), nylas, message.getData().getFrom().get(0).getEmail());
                        if (!contact.getData().get(0).getId().isEmpty()) {
                            // If the contact is available, downloads its profile picture
                            download_contact_picture(dotenv.get("GRANT_ID"), nylas, contact.getData().get(0).getId());
                        }
                        // Remove extra information from the message, like appended
                        //  message, email and phone number
                        String parsed_message = message.getData().getBody();
                        assert parsed_message != null;
                        parsed_message = parsed_message.replaceAll("\\\\n", "\n\n");
                        parsed_message = Jsoup.clean(parsed_message, Safelist.basic());
                        // Phone number
                        String pattern_key = "\\d{3}[- .]\\d{3}[- .]\\d{4}";
                        parsed_message = parsed_message.replaceAll(pattern_key, "");
                        // Email
                        pattern_key = "(?i)[A-Z0-9+_.\\-]+@[A-Z0-9.\\-]+";
                        parsed_message = parsed_message.replaceAll(pattern_key, "");
                        // Replied message history
                        pattern_key = "(?s)(\\bOn.*\\b)(?!.*\\1).+";
                        parsed_message = parsed_message.replaceAll(pattern_key, "");
                        // Twitter handler
                        pattern_key = "(?i)twitter:.+";
                        parsed_message = parsed_message.replaceAll(pattern_key, "");
                        body = parsed_message;
                        // Convert date to something readable
                        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(message.getData().getDate()), ZoneOffset.UTC);
                        String date = ldt.getYear() + "-" + ldt.getMonthValue() + "-" + ldt.getDayOfMonth();
                        String time = ldt.getHour() + ":" + ldt.getMinute() + ":" + ldt.getSecond();
                        // If there's no contact
                        if (contact.getData().get(0).getId().isEmpty()) {
                            pictures = "NotFound.png";
                            names = "Not Found" + " on" + date + " at " + time;
                        } else {
                            // If there's a contact, pass picture information,
                            // name and date and time of message
                            pictures = contact.getData().get(0).getGivenName() + "_" + contact.getData().get(0).getSurname() + ".png";
                            names = contact.getData().get(0).getGivenName() + " " +
                                    contact.getData().get(0).getGivenName() + " on " +
                                    date + " at " + time;
                        }
                        main_thread new_mainThread = new main_thread();
                        new_mainThread.setThread(subject);
                        new_mainThread.setMessage(body);
                        new_mainThread.setNames(names);
                        new_mainThread.setPicture(pictures);
                        data_threads.add(new_mainThread);
                    }
                    all_threads.add(new ArrayList<>(data_threads));
                    data_threads.clear();
                }
            }

            // Hashmap to send parameters to our handlebars view
            Map<String, ArrayList<ArrayList<main_thread>>> thread_map = new HashMap<String, ArrayList<ArrayList<main_thread>>>();
            // We're passing the same _threads ArrayList twice
            // as we need the copy for processing
            thread_map.put("threads", all_threads);
            thread_map.put("inner_threads", all_threads);

            // Call the handlebars template
            return new ModelAndView(thread_map, "main.hbs");
        }, new HandlebarsTemplateEngine());
    }
}
