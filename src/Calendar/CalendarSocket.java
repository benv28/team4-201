package Calendar;

import com.google.gson.Gson;
import db.CalendarBase;
import db.Event;
import db.Room;
import db.User;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;

@ServerEndpoint(value = "/CalendarSocket")
public class CalendarSocket {
    enum CalendarMessageType {
        INIT(0), ADD_EVENT(1), UPDATE(2);

        private int i;

        CalendarMessageType(int i) {
            this.i = i;
        }

        public int getI() {
            return i;
        }
    }

    class CalendarData {
        private CalendarMessageType messageType;
        private String userID;
        private String roomID;
        private String jsonData;

        public CalendarData(CalendarMessageType messageType, String userID, String roomID, String jsonData) {
            this.messageType = messageType;
            this.userID = userID;
            this.roomID = roomID;
            this.jsonData = jsonData;
        }

        public CalendarMessageType getMessageType() {
            return messageType;
        }

        public String getUserID() {
            return userID;
        }

        public String getRoomID() {
            return roomID;
        }

        public String getJsonData() {
            return jsonData;
        }
    }

    class CalendarSession {
        private String userID;
        private String roomID;
        private Session session;

        public CalendarSession(String userID, String roomID, Session session) {
            this.userID = userID;
            this.roomID = roomID;
            this.session = session;
        }

        public String getUserID() {
            return userID;
        }

        public String getRoomID() {
            return roomID;
        }

        public Session getSession() {
            return session;
        }
    }

    class PrimitiveUser {
        private String userID;
        private String username;
        private ArrayList<CalendarEvent> events;

        public PrimitiveUser(String userID, String username, ArrayList<CalendarEvent> calendarEvents) {
            this.userID = userID;
            this.username = username;
            this.events = calendarEvents;
        }
    }

    class CalendarEvent {
        private String userID;
        private String eventSummary;
        private GregorianCalendar startDateTime;
        private GregorianCalendar endDateTime;

        /**
         * Constructor for a new CalendarEvent. All parameters must be not null.
         *
         * @param eventID The ID of this event.
         * @param eventSummary Short title that describes the event.
         * @param startDateTime A GregorianCalendar that represents the starting date and time.
         * @param endDateTime A GregorianCalendar the represents the ending date and time.
         */
        public CalendarEvent(String eventID, String eventSummary, GregorianCalendar startDateTime, GregorianCalendar endDateTime) {
            this.userID = eventID;
            this.eventSummary = eventSummary;
            this.startDateTime = startDateTime;
            this.endDateTime = endDateTime;
        }

        public String getUserID() {
            return userID;
        }

        public String getEventSummary() {
            return eventSummary;
        }

        public GregorianCalendar getStartDateTime() {
            return startDateTime;
        }

        public GregorianCalendar getEndDateTime() {
            return endDateTime;
        }
    }

    private static Gson gson = new Gson();
    private static Vector<CalendarSession> calendarSessions = new Vector<>();

    @OnOpen
    public void open(Session session) {
        System.out.println("Connection made.");
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        CalendarData calendarData = gson.fromJson(message, CalendarData.class);

        System.out.println("Message received of type: " + calendarData.getMessageType());

        switch (calendarData.getMessageType()) {
            case INIT:
                //store the session's data as a CalendarSession
                CalendarSession calendarSession = new CalendarSession(calendarData.getUserID(), calendarData.getRoomID(), session);
                calendarSessions.add(calendarSession);

                //retrieve the necessary data to send to this session
                ArrayList<PrimitiveUser> primitiveUsers = new ArrayList<>();
                CalendarBase calendarBase = new CalendarBase();
                ArrayList<User> users = calendarBase.retrieveUsers(calendarData.roomID);

                //user events
                for (User foo : users) {
                    String userID = foo.getUserID();
                    String name = foo.getFullName();

                    ArrayList<Event> retrievedEvents = new CalendarBase().retrieveEvents(userID);
                    ArrayList<CalendarEvent> userCalendarEvents = new ArrayList<>();
                    for (var bar : retrievedEvents) {
                        userCalendarEvents.add(new CalendarEvent(bar.getUserID(), bar.getEventSummary(), gson.fromJson(bar.getStartDateTime(), GregorianCalendar.class), gson.fromJson(bar.getEndDateTime(), GregorianCalendar.class)));
                    }

                    primitiveUsers.add(new PrimitiveUser(userID, name, userCalendarEvents));
                }

                //room events
                ArrayList<Event> roomEvents = new CalendarBase().retrieveEvents(calendarSession.roomID);
                ArrayList<CalendarEvent> roomCalendarEvents = new ArrayList<>();
                for (var bar : roomEvents) {
                    roomCalendarEvents.add(new CalendarEvent(bar.getUserID(), bar.getEventSummary(), gson.fromJson(bar.getStartDateTime(), GregorianCalendar.class), gson.fromJson(bar.getEndDateTime(), GregorianCalendar.class)));
                }
                primitiveUsers.add(new PrimitiveUser(calendarSession.roomID, "Room", roomCalendarEvents));

                //jsonify it into a payload
                String jsonMessage = gson.toJson(primitiveUsers);

                //create a new CalendarData message to encapsulate the data we're sending back
                calendarData = new CalendarData(CalendarMessageType.INIT, calendarSession.getUserID(), calendarSession.getRoomID(), jsonMessage);

                //send the json'd INIT response data back
                try {
                    session.getBasicRemote().sendText(gson.toJson(calendarData));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                break;
            case ADD_EVENT:
                CalendarEvent calendarEvent = gson.fromJson(calendarData.getJsonData(), CalendarEvent.class);

                new CalendarBase().addEvent(new Event(calendarEvent.getUserID(), calendarEvent.getEventSummary(), gson.toJson(calendarEvent.getStartDateTime()), gson.toJson(calendarEvent.getEndDateTime())));

                for (var foo : calendarSessions) {
                    //determines if this message be sent to this user's session
                    boolean needsUpdating = foo.getUserID().equals(calendarData.getUserID());

                    ArrayList<User> bar = new CalendarBase().retrieveUsers(foo.getRoomID());
                    for (User baz : bar) {
                        if (baz.getUserID().equals(calendarData.getUserID())) {
                            needsUpdating = true;
                        }
                    }

                    if (needsUpdating) {
                        try {
                            foo.getSession().getBasicRemote().sendText(gson.toJson(new CalendarData(CalendarMessageType.UPDATE, foo.getUserID(), foo.getRoomID(), gson.toJson(calendarEvent))));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                break;
            case UPDATE:
                //SHOULD NEVER BE REACHED....?????
                break;
        }
    }

    @OnClose
    public void close(Session session) {
        calendarSessions.removeIf(foo -> (foo.getSession().equals(session)));

        System.out.println("Closed a session.");
    }

    @OnError
    public void error(Throwable throwable) {
        throwable.printStackTrace();
    }

    public static void main(String [] args) {
        CalendarBase cb = new CalendarBase();
        for(int i = 0; i < 20; i++) {
            int day = 11 + (int) Math.floor(Math.random() * 7);
            int hour = (int) Math.floor(Math.random() * 18);


            var std = new GregorianCalendar(2018, Calendar.NOVEMBER, day, hour, 0);
            std.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
            var etd = new GregorianCalendar(2018, Calendar.NOVEMBER, day, hour + (int)Math.floor(Math.random() * 6), 0);
            etd.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));

            Event tmp = null;

            int j = (int) (Math.random() * 6);
            switch (j) {
                case 0:
                    tmp = new Event("yeqing@usc.edu", "MyEvent #" + i, gson.toJson(std), gson.toJson(etd));
                    break;
                case 1:
                    tmp = new Event("strawsnowrries@gmail.com", "MyEvent #" + i, gson.toJson(std), gson.toJson(etd));
                    break;
                case 2:
                    tmp = new Event("voter@usc.edu", "MyEvent #" + i, gson.toJson(std), gson.toJson(etd));
                    break;
                case 3:
                    tmp = new Event("msteinberg@usc.edu", "MyEvent #" + i, gson.toJson(std), gson.toJson(etd));
                    break;
                case 4:
                    tmp = new Event("kchandr@usc.edu", "MyEvent #" + i, gson.toJson(std), gson.toJson(etd));
                    break;
                default:
                    tmp = new Event("691337", "RoomEvent #" + i, gson.toJson(std), gson.toJson(etd));
                    break;
            }

            cb.addEvent(tmp);
        }

        User user1 = new User("Qing Ye", "yeqing@usc.edu", "691337", "imgurl");
        User user2 = new User("Allan Zhang", "strawsnowrries@gmail.com", "691337", "imgurl");
        User user3 = new User("Ben Voter", "voter@usc.edu", "691337", "imgurl");
        User user4 = new User("Micah Steinberg", "msteinberg@usc.edu", "691337", "imgurl");
        User user5 = new User("Katrina Chandra", "kchandr@usc.edu", "691337", "imgurl");

        cb.addUser(user1);
        cb.addUser(user2);
        cb.addUser(user3);
        cb.addUser(user4);
        cb.addUser(user5);

        ArrayList<String> residents = new ArrayList<>();
        residents.add("yeqing@usc.edu");
        residents.add("strawsnowrries@gmail.com");
        residents.add("voter@usc.edu");
        residents.add("msteinberg@usc.edu");
        residents.add("kchandr@usc.edu");
        Room room = new Room("691337", residents, "DO NOT DISTURB");
        cb.addRoom(room);

        ArrayList<User> users = cb.retrieveUsers("691337");
        for (User foo : users) {
            System.out.println(foo.getFullName());
        }
    }
}

//                var std1 = new GregorianCalendar(2018, Calendar.NOVEMBER, 10, 12, 0);
//                std1.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
//                var etd1 = new GregorianCalendar(2018, Calendar.NOVEMBER, 10, 15, 0);
//                etd1.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
//                CalendarEvent event1 = new CalendarEvent("strawsnowrries@gmail.com", "Cool CalendarEvent #1", std1, etd1);
//
//                var std2 = new GregorianCalendar(2018, Calendar.NOVEMBER, 12, 9, 0);
//                std2.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
//                var etd2 = new GregorianCalendar(2018, Calendar.NOVEMBER, 12, 10, 30);
//                etd2.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
//                CalendarEvent event2 = new CalendarEvent("strawsnowrries@gmail.com", "Cool CalendarEvent #2", std2, etd2);
//
//                ArrayList<CalendarEvent> eventArrayList1 = new ArrayList<>();
//                eventArrayList1.add(event1);
//                eventArrayList1.add(event2);
//                primitiveUsers.add(new PrimitiveUser("strawsnowrries@gmail.com", "Allan Zhang", eventArrayList1));
//
//                var std3 = new GregorianCalendar(2018, Calendar.NOVEMBER, 9, 11, 0);
//                std3.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
//                var etd3 = new GregorianCalendar(2018, Calendar.NOVEMBER, 9, 17, 0);
//                etd3.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
//                CalendarEvent event3 = new CalendarEvent("allanzha@usc.edu", "Cool CalendarEvent #1", std3, etd3);
//
//                var std4 = new GregorianCalendar(2018, Calendar.NOVEMBER, 12, 9, 30);
//                std4.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
//                var etd4 = new GregorianCalendar(2018, Calendar.NOVEMBER, 12, 11, 30);
//                etd4.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
//                CalendarEvent event4 = new CalendarEvent("allanzha@usc.edu", "Cool CalendarEvent #2", std4, etd4);
//
//                ArrayList<CalendarEvent> eventArrayList2 = new ArrayList<>();
//                eventArrayList2.add(event3);
//                eventArrayList2.add(event4);
//                primitiveUsers.add(new PrimitiveUser("allanzha@usc.edu", "Ballin' Zhang", eventArrayList2));
//                //END DUMMY DATA..