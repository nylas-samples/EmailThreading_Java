// Import Java Utilities
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import okhttp3.ResponseBody;
import java.nio.file.Paths;
import java.nio.file.Files;

// Import Spark and Handlebars libraries
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import spark.ModelAndView;
import static spark.Spark.*;
import spark.template.handlebars.HandlebarsTemplateEngine;

//Import Nylas Packages
import com.nylas.*;
import com.nylas.Thread;

//Import DotEnv to handle .env files
import io.github.cdimascio.dotenv.Dotenv;

public class EmailThreading {

    static RemoteCollection<Contact> get_contact(Contacts _contact, String email) throws RequestFailedException, IOException {
        RemoteCollection<Contact> contact_list = _contact.list(new ContactQuery().email(email));
        return contact_list;
    }

    static void download_contact_picture(NylasAccount account, String id) throws RequestFailedException, IOException{
        try (ResponseBody picResponse = account.contacts().downloadProfilePicture(id)) {
            Files.copy(picResponse.byteStream(), Paths.get("src/main/resources/public/images/" + id +".png"));
        }catch (Exception e){
            System.out.println("Image was already downloaded");
        }
    }

    public static void main(String[] args) {
        staticFiles.location("/public");
        // Load the .env file
        Dotenv dotenv = Dotenv.load();
        // Create the client object
        NylasClient client = new NylasClient();
        // Connect it to Nylas using the Access Token from the .env file
        NylasAccount account = client.account(dotenv.get("ACCESS_TOKEN"));
        // Get access to messages
        Messages messages = account.messages();
        // Get access to contacts
        Contacts contacts = account.contacts();
        // Default path when we load our web application

        // Hashmap to send parameters to our handlebars view
        Map map = new HashMap();
        map.put("search", "");

        get("/", (request, response) ->
                        // Create a model to pass information to the handlebars template
                        // Call the handlebars template
                        new ModelAndView(map, "main.hbs"),
                new HandlebarsTemplateEngine());

        // When we submit the form, we're posting data
        post("/", (request, response) -> {
            // Get parameter from form
            String search = request.queryParams("search");
            // Search all threads related to the email address
            Threads threads = account.threads();
            List<Thread> thread = threads.list(new ThreadQuery().
                    in("inbox").from(search)).fetchAll();
            if (search.equals("")) {
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

            // This ArrayList will hold all the threads with their
            // accompanying information
            ArrayList<ArrayList<ArrayList<String>>> _threads = new ArrayList<ArrayList<ArrayList<String>>>();

            // Look for threads with more than 1 message
            for (Thread msg_thread : thread) {
                // Auxiliary variables
                ArrayList<ArrayList<String>> _thread = new ArrayList<ArrayList<String>>();
                ArrayList<String> _messages = new ArrayList<String>();
                ArrayList<String> aux_messages = new ArrayList<String>();
                ArrayList<String> _pictures = new ArrayList<String>();
                ArrayList<String> _names = new ArrayList<String>();

                // Only add threads with two messages or more
                if (msg_thread.getMessageIds().size() > 1) {
                    // Get the subject of the first email
                    aux_messages.add(msg_thread.getSubject());
                    _thread.add(aux_messages);
                    // Loop through all messages contained in the thread
                    for (String message_ids : msg_thread.getMessageIds()) {
                        // Get information from the message
                        Message message = messages.get(message_ids);
                        // Try to get the contact information
                        RemoteCollection<Contact> contact = get_contact(contacts, message.getFrom().get(0).getEmail());
                        if (contact != null && !contact.fetchAll().get(0).getId().isEmpty()) {
                            // If the contact is available, downloads its profile picture
                            download_contact_picture(account, contact.fetchAll().get(0).getId());
                        }
                        // Remove extra information from the message, like appended
                        //  message, email and phone number
                        String parsed_message = message.getBody();
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
                        pattern_key = "(?i)<span>twitter:.+";
                        parsed_message = parsed_message.replaceAll(pattern_key, "");
                        _messages.add(parsed_message);
                        // Convert date to something readable
                        LocalDateTime ldt = LocalDateTime.ofInstant(message.getDate(), ZoneOffset.UTC);
                        String date = ldt.getYear() + "-" + ldt.getMonthValue() + "-" + ldt.getDayOfMonth();
                        String time = ldt.getHour() + ":" + ldt.getMinute() + ":" + ldt.getSecond();
                        // If there's no contact
                        if (contact == null || contact.fetchAll().get(0).getId().isEmpty()) {
                            _pictures.add("NotFound.png");
                            _names.add("Not Found" + " on" + date + " at " + time);
                        } else {
                            // If there's a contact, pass picture information,
                            // name and date and time of message
                            _pictures.add(contact.fetchAll().get(0).getId() + ".png");
                            _names.add(contact.fetchAll().get(0).getGivenName() + " " +
                                    contact.fetchAll().get(0).getSurname() + " on " +
                                    date + " at " + time);
                        }

                    }
                // Add ArrayLists to main thread arraylist
                // and then add them all
                _thread.add(_messages);
                _thread.add(_pictures);
                _thread.add(_names);
                _threads.add(_thread);
                }
            }

            // Hashmap to send parameters to our handlebars view
            Map thread_map = new HashMap();
            // We're passing the same _threads ArrayList twice
            // as we need the copy for processing
            thread_map.put("threads", _threads);
            thread_map.put("inner_threads", _threads);

            // Call the handlebars template
            return new ModelAndView(thread_map, "main.hbs");
        }, new HandlebarsTemplateEngine());
    }
}
