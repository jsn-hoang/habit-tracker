package com.example.gittersandsittersdatabase;

import static android.content.ContentValues.TAG;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import org.w3c.dom.Text;


import java.io.File;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * This Activity is responsible for creating, editing, or deleting an Event.
 * This Activity has two "modes": newEvent, and editEvent
 * newEvent mode starts when the the user navigates to this Activity from HabitActivity
 * editEvent mode starts when the the user navigates to this Activity from EventHistoryActivity
 */

//TODO Fragments for habitEventPhoto and habitEventLocation

public class AddRemoveEventActivity extends AppCompatActivity {

    // Declare variables for referencing
    private FirebaseFirestore db;
    private CollectionReference collectionRef;
    public static final int RESULT_DELETE = 2;
    User user;
    Habit habit;                   // The parent Habit of the HabitEvent
    HabitEvent habitEvent;
    private String habitEventID;
    Calendar habitEventDate;
    Location habitEventLocation = null;
    File habitEventPhoto = null;
    boolean isNewHabitEvent;
    int habitListIndex;            // index position of the Habit in the User's habitList
    int habitEventListIndex;       // index position of the HabitEvent in the Habit's habitEventList

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_remove_habit_event);

        // get user
        user = (User) getIntent().getSerializableExtra("user");
        // get the Habit that is/will be the parent of this HabitEvent
        habit = (Habit) getIntent().getSerializableExtra("habit");
        // get the index position of the parentHabit in habitList
        habitListIndex = user.getUserHabitPosition(habit);
        // get instance of FirebaseFirestore
        db = FirebaseFirestore.getInstance();
        // get collection reference of HabitEvents
        collectionRef = db.collection("Users/" + user.getUserID() + "/Habits/" + habit.getHabitID() + "/HabitEvents/");

        // position intent is only available for an existing HabitEvent
        if (getIntent().hasExtra("position")) {
            isNewHabitEvent = false;
            habitEventListIndex = getIntent().getExtras().getInt("position");
            // get the HabitEvent to be edited
            habitEvent = habit.getHabitEvent(habitEventListIndex);
        }
        // else this is a new HabitEvent
        else isNewHabitEvent = true;


        // Declare variables for xml object referencing
        EditText habitEventNameEditText = findViewById(R.id.event_name_editText);
        EditText habitEventCommentEditText = findViewById(R.id.event_comment_editText);
        final Button deleteButton = findViewById(R.id.delete_event_button);
        final Button addButton = findViewById(R.id.add_event_button);
        final Button cancelButton = findViewById(R.id.cancel_event_button);
        final TextView header = findViewById(R.id.add_edit_event_title_text);
        final TextView eventDateText = findViewById(R.id.event_date_text);
        final Button eventLocationButton = findViewById(R.id.event_location_button);
        final ImageButton eventPhotoButton = findViewById(R.id.event_photo_button);

        // Set up activity layout
        activityLayoutSetup(isNewHabitEvent, header, addButton, deleteButton);

        // Set HabitEvent date and date TextView
        setHabitEventDateAndField(eventDateText);

        // Set up remaining fields for existing HabitEvent
        if (!isNewHabitEvent) {

            // Set name and comment fields
            habitEventNameEditText.setText(habitEvent.getEventName());
            habitEventCommentEditText.setText(habitEvent.getEventComment());

            //TODO set the location and photo fields
            Location habitEventLocation = habitEvent.getEventLocation();
            File  habitEventPhoto = habitEvent.getEventPhoto();

        }

        // This Listener is responsible for the logic when clicking the "OK" button
        addButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                // Retrieve user inputted data
                String habitEventName = habitEventNameEditText.getText().toString();
                String habitEventComment = habitEventCommentEditText.getText().toString();

                //TODO: get the user inputted location and photo fields

                // Note: habitEventDate is already done


                if (isNewHabitEvent) {
                    // Create a new HabitEvent
                    habitEvent = new HabitEvent(habitEventName, habit.getHabitName(),
                            habitEventDate, habitEventComment);
                    // Add the habitEvent to Firestore
                    addHabitEventToDB();
                    // assign the HabitEventID
                    habitEvent.setEventID(habitEventID);
                    // Add the new HabitEvent to the Habit's habitEventList
                    habit.addHabitEvent(habitEvent);


                }
                else { // else edit the existing HabitEvent

                    String previousEventName = habitEvent.getEventName();
                    habitEvent.setEventName(habitEventName);
                    habitEvent.setEventLocation(habitEventLocation);
                    habitEvent.setEventComment(habitEventComment);
                    habitEvent.setEventPhoto(habitEventPhoto);
                    // Overwrite the edited HabitEvent
                    habit.setHabitEvent(habitEventListIndex, habitEvent);
                    // Update the edited HabitEvent in FireStore
                    setHabitEventInDB();
                }

                // Overwrite the edited user Habit
                user.setUserHabit(habitListIndex, habit);

                // Navigate back to launcher Activity (HabitActivity or HabitEventActivity)
                Intent intent = new Intent();
                intent.putExtra("user", user);
                setResult(RESULT_OK, intent);
                finish();
            }
        });


        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // remove the habitEvent from the corresponding Habit
                habit.deleteHabitEvent(habitEvent);
                // overwrite the edited userHabit
                user.setUserHabit(habitListIndex, habit);
                // Delete the habitEvent from Firestore
                deleteHabitEventFromDB(habitEvent);


                // Navigate back to MainActivity
                Intent intent = new Intent();
                intent.putExtra("user", user);
                setResult(RESULT_DELETE, intent);
                finish();
            }
        });

    }

    /**
     * This method sets the Activity and button text to the appropriate titles
     * given whether the user is creating a new habit, or editing and existing one.
     * @param isNewActivityMode - Boolean indicating whether user is creating a new event
     * @param header        - A TextView object that displays the Title of the activity
     * @param addButton     - Button for creating or updating a event
     * @param deleteButton  - Button for deleting an existing event
     */
    private void activityLayoutSetup(boolean isNewActivityMode,
                                     TextView header, Button addButton, Button deleteButton) {

        // get the parent Habit name (to be displayed in header)
        String habitName = habit.getHabitName();

        if (isNewActivityMode){
            // Make activity layout correspond to mode ADD
            header.setText("Add a New " + habitName + " Event");
            // deleteButton disappears, add button says CREATE
            deleteButton.setVisibility(View.GONE);
            addButton.setText("CREATE");
        }
        else {  // Make activity layout correspond to mode EDIT
            header.setText("Edit " + habitName + " Event");
            // add button says UPDATE
            addButton.setText("UPDATE");
        }
    }

    /**
     * This method initializes a TextView object to a particular date
     * For an existing HabitEvent, the Textview object is set to the existing date
     * For a new HabitEvent, the Textview object is set to today's date
     * @param eventDateText - TextView object that will be set to the HabitEvent's start date
     */
    public void setHabitEventDateAndField(TextView eventDateText) {

        // Create Calendar object
        Calendar c = Calendar.getInstance();

        // for new HabitEvent set c to today's date
        if (isNewHabitEvent) {
            // Get today's date
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);
            // set c to today's date;
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, day);

        }
        else { // for existing HabitEvent, set c to existing HabitEvent date
            c = habitEvent.getEventDate();
        }
        // Assign c to habitEventDate
        habitEventDate = c;
        // Convert Calendar object to String
        String dateString = DateFormat.getDateInstance().format(c.getTime());
        // Set String representation of date to eventDateText
        eventDateText.setText(dateString);
    }

    /**
     * This method adds a HabitEvent to Firestore.
     */
    public void addHabitEventToDB() {

        HashMap<String, Object> data = new HashMap<>();

        data.put("eventName", habitEvent.getEventName());
        // Convert startDate to type long for database storage
        long longDate = habitEvent.getEventDate().getTimeInMillis();
        data.put("longDate", longDate);
        //data.put("eventLocation", habitEvent.getEventLocation());
        data.put("eventComment", habitEvent.getEventComment());
        //data.put("eventPhoto", habitEvent.getEventPhoto());

        // Add habitEvent to the Habit's habitEvent Collection
        collectionRef.add(data)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "DocumentSnapshot written with ID: " + documentReference.getId());
                        habitEventID = documentReference.getId();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });
    }

    /**
     * This method deletes a HabitEvent from Firestore.
     */
    public void deleteHabitEventFromDB(HabitEvent habitEvent) {

        collectionRef.document(habitEvent.getEventID())
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Data has been deleted successfully!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG, "Data could not be deleted!" + e.toString());
                    }
                });
    }

    /**
     * This method replaces a HabitEvent in the logged in user's HabitEvent collection in the database
     */
    public void setHabitEventInDB() {

        long longDate = habitEvent.getEventDate().getTimeInMillis();
        DocumentReference docRef = collectionRef.document(habitEvent.getEventID());
        docRef.update(
                "eventName", habitEvent.getEventName(),
                "longDate", longDate,
                "eventComment", habitEvent.getEventComment())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully updated!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error updating document", e);
                    }
                });
    }
}

