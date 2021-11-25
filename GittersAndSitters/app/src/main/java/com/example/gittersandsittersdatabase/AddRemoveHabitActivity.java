package com.example.gittersandsittersdatabase;

import static android.content.ContentValues.TAG;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;


/**
 * This class is responsible for creating, editing, or deleting a Habit.
 */

public class AddRemoveHabitActivity extends AppCompatActivity implements DatePickerDialog.OnDateSetListener, FirestoreHabitCallback, Executor{

    private FirebaseFirestore db;
    private CollectionReference collectionRef;

    // Declare variables for referencing
    public static final int RESULT_DELETE = 2;

    User user;
    Habit habit;
    boolean isNewHabit;
    int habitIndexPosition;
    EditText habitNameEditText;
    Calendar habitStartDate;
    TextView habitStartDateText;
    ArrayList<CheckBox> weekdayCheckBoxes;
    EditText habitReasonEditText;
    private DataUploader dataUploader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_remove_habit);

        // get user
        user = (User) getIntent().getSerializableExtra("user");

        db = FirebaseFirestore.getInstance();
        collectionRef = db.collection("Users/" + user.getUserID() + "/Habits/");


        // habitPosition corresponds to which ListView entry was clicked
        if (getIntent().hasExtra("position")) {
            habitIndexPosition = getIntent().getExtras().getInt("position");
            isNewHabit = false;
        }
        else isNewHabit = true;     // if no position passed, then this is a new Habit


        dataUploader = new DataUploader(user.getUserID());

        // Get views that will be used for user input
        habitNameEditText = findViewById(R.id.habit_name_editText);
        habitStartDateText = findViewById(R.id.habit_start_date_text);
        weekdayCheckBoxes = new ArrayList<>();
        CheckBox monday = findViewById(R.id.monday_checkbox);
        CheckBox tuesday = findViewById(R.id.tuesday_checkbox);
        CheckBox wednesday = findViewById(R.id.wednesday_checkbox);
        CheckBox thursday = findViewById(R.id.thursday_checkbox);
        CheckBox friday = findViewById(R.id.friday_checkbox);
        CheckBox saturday = findViewById(R.id.saturday_checkbox);
        CheckBox sunday = findViewById(R.id.sunday_checkbox);

        // Populate weekdayCheckBoxes ArrayList
        List<CheckBox> checkBoxes = Arrays.asList
                (sunday, monday, tuesday, wednesday, thursday, friday, saturday);
        weekdayCheckBoxes.addAll(checkBoxes);

        habitReasonEditText = findViewById(R.id.habit_reason_editText);
        final Button deleteButton = findViewById(R.id.delete_habit_button);
        final Button addButton = findViewById(R.id.add_habit_button);
        final Button cancelButton = findViewById(R.id.cancel_habit_button);
        final Button datePickerButton = findViewById(R.id.date_picker_button);
        final TextView header = findViewById(R.id.add_edit_habit_title_text);


        // Set up activity layout
        activityLayoutSetup(isNewHabit, header, addButton, deleteButton);

        // Set up Date field
        setDateField(isNewHabit, habitStartDateText);

        // Set up remaining fields for existing habit
        if (!isNewHabit) {

            // Get the habit to be edited
            habit = user.getUserHabit(habitIndexPosition);

            // Set name and reason fields
            habitNameEditText.setText(habit.getHabitName());
            habitReasonEditText.setText(habit.getHabitReason());

            // Set checkboxes
            setDaysToCheckBoxes(habit, weekdayCheckBoxes);

            //TODO: Set the habitPublic boolean
        }


        // This Listener is responsible for the logic when clicking the confirm button
        addButton.setOnClickListener(v -> {

            // Retrieve user inputted data
            String habitName = habitNameEditText.getText().toString();
            String habitReason = habitReasonEditText.getText().toString();

            // Get user selected days from checked boxes
            ArrayList<Integer> weekdays = getDaysFromCheckBoxes();

            // habitStartDate is already retrieved

            //TODO: Get the habitPublic boolean


            if (isNewHabit) {
                // Create a new Habit (habitID will be properly set when Habit is added to db
                habit = new Habit(habitName, weekdays, habitStartDate, habitReason, true);

                // Add the habit to db
                dataUploader.addHabitAndGetID(new FirestoreHabitCallback() {
                    @Override
                    // Get the modified Habit with HabitId
                    public void onHabitCallback(Habit habitWithID) {
                        user.addUserHabit(habitWithID);
                    }
                }, habit);


            }
            else { // else edit the existing habit
                String previousName = habit.getHabitName();
                habit.setHabitName(habitName);
                habit.setWeekdays(weekdays);
                habit.setStartDate(habitStartDate);
                habit.setHabitReason(habitReason);
                // Overwrite the previous habit with the edited one
                user.setUserHabit(habitIndexPosition, habit);
                // Edit the document in Firestore
                dataUploader.setHabitInDB(habit);
            }
            // Navigate back to MainActivity
            Intent intent = new Intent();
            intent.putExtra("user", user);
            setResult(RESULT_OK, intent);
            finish();
        });


        cancelButton.setOnClickListener(view -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        deleteButton.setOnClickListener(view -> {

            // Delete habit from user habitList
            user.deleteUserHabit(habit);

            collectionRef = collectionRef.document(habit.getHabitID()).collection("HabitEvents");

            dataUploader.deleteCollection(collectionRef, new Executor() {
                @Override
                public void execute(Runnable command) {
                    // Delete habit from user db
                    dataUploader.deleteHabit(habit);
                }
            });

            // Delete habit from user db
            //dataUploader.deleteHabit(habit);

            // Navigate back to MainActivity
            Intent intent = new Intent();
            intent.putExtra("user", user);
            setResult(RESULT_DELETE, intent);
            finish();
        });

        /** This listener is responsible for the logic
         * when clicking the date picker button
         */
        datePickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Create bundle to pass data
                Bundle b = new Bundle();
                b.putLong("date", habitStartDate.getTimeInMillis());
                // Pass bundle to fragment
                DialogFragment datePickerFragment = new DatePickerFragment();
                datePickerFragment.setArguments(b);
                datePickerFragment.show(getSupportFragmentManager(), "ADD_START_DATE");
            }
        });
    }

    /** This method sets habitStartDate to the chosen DatePicker date
     * @param view
     * @param year
     * @param month
     * @param day
     */
    @Override
    public void onDateSet(DatePicker view, int year, int month, int day){

        // Get chosen habitStartDate
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        habitStartDate = cal;

        // Convert to Calendar object to String
        String dateString = DateFormat.getDateInstance().format(cal.getTime());
        // Set dateString to TextView
        habitStartDateText.setText(dateString);
    }

    /**
     * This method sets the Activity and button text to the appropriate titles
     * given whether the user is creating a new habit, or editing and existing one.
     * @param isNewActivityMode - Boolean indicating whether user is creating a new habit
     * @param header        - A TextView object that displays the Title of the activity
     * @param addButton     - Button for creating or updating a habit
     * @param deleteButton  - Button for deleting an existing habit
     */
    private void activityLayoutSetup(boolean isNewActivityMode,
                                     TextView header, Button addButton, Button deleteButton) {

        if (isNewActivityMode){
            // Make activity layout correspond to mode ADD
            header.setText("Add a New Habit");
            // deleteButton disappears, add button says CREATE
            deleteButton.setVisibility(View.GONE);
            addButton.setText("CREATE");
        }
        else {  // Make activity layout correspond to mode EDIT
            header.setText("Edit Habit");
            // add button says UPDATE
            addButton.setText("UPDATE");
        }
    }

    /**
     * This method initializes a TextView object to a particular date
     * For an existing habit, the Textview object is set to the existing start date
     * For a new habit, the Textview object is set to today's date
     * @param isNewHabit        - boolean representing whether this is a new habit
     * @param habitStartDateText - TextView object that will be set to the habit's start date
     */
    public void setDateField(boolean isNewHabit, TextView habitStartDateText) {

        if (isNewHabit) {
            // Initialize habitStartDate TextView to today's date
            habitStartDate = Calendar.getInstance();
            // Get today's date
            int year = habitStartDate.get(Calendar.YEAR);
            int month = habitStartDate.get(Calendar.MONTH);
            int day = habitStartDate.get(Calendar.DAY_OF_MONTH);
            // set cal to today's date;
            habitStartDate.set(Calendar.YEAR, year);
            habitStartDate.set(Calendar.MONTH, month);
            habitStartDate.set(Calendar.DAY_OF_MONTH, day);
        }
        else { // This is an existing Habit with an existing startDate
            Habit habit = user.getUserHabit(habitIndexPosition);
            // Get habitStartDate
            habitStartDate = habit.getStartDate();
        }
        // Convert Calendar object to String
        String dateString = DateFormat.getDateInstance().format(habitStartDate.getTime());
        // Set String representation of date to startDate TextView
        habitStartDateText.setText(dateString);
    }

    /**
     * This method sets checkboxes to checked or not, depending
     * on if the existing habit is due on that particular day
     * @param habit - The habit whose habit.getWeekdays() we are interested
     * @param checkBoxes - a list of checkboxes whose statuses (checked/unchecked)
     *                     will be determined by what weekdays the habit occurs on
     */
    public void setDaysToCheckBoxes(Habit habit, ArrayList<CheckBox> checkBoxes) {

        // Check off all CheckBoxes that correspond to current Habit weekdays
        for (int i = 0; i < checkBoxes.size(); i++) {
            // if the habit is scheduled on weekday(i+1)
            if (habit.getWeekdays().contains(i + 1)) {    // CheckBox i corresponds to day i+1
                // Check off box(i)
                checkBoxes.get(i).setChecked(true);
            }
        }
    }
    /**
     * This method populates a list of integers depending on whether checkboxes are checked
     * If a checkbox representing a certain day is checked, then the integer representing that day
     * is added to the list (Sunday = 1, Monday = 2, ..., Saturday = 6)
     * @return - ArrayList<Integer> corresponding to days of the week
     */
    public ArrayList<Integer> getDaysFromCheckBoxes() {

        // Initialize new weekdays arraylist
        ArrayList<Integer> days = new ArrayList<>();
        // Loop through all of the checkboxes
        for (int i = 0; i < weekdayCheckBoxes.size(); i++) {
            // if checkBox(i) is checked
            if (weekdayCheckBoxes.get(i).isChecked()) {
                // add i to weekdays
                days.add(i + 1);        // CheckBox i corresponds to day i+1
            }
        }
        return days;
    }

//    /**
//     * This method adds a newly created habit to the logged in user's Habit collection in the database
//     */
//    public void addHabitToDB() {
//
//        HashMap<String, Object> data = new HashMap<>();
//        // Habit(String habitName, ArrayList<Integer> weekdays, Calendar startDate, String habitReason, boolean habitPublic)
//        data.put("habitName", habit.getHabitName());
//        data.put("weekdays", habit.getWeekdays());
//        // Convert startDate to type long for database storage
//        long longDate = habit.getStartDate().getTimeInMillis();
//        data.put("longDate", longDate);
//        data.put("reason", habit.getHabitReason());
//        data.put("isPublic", habit.isHabitPublic());
//
//        // Add habit to the User's Habit Collection
//        collectionRef.add(data)
//                .addOnSuccessListener(documentReference -> {
//                    Log.d(TAG, "DocumentSnapshot written with ID: " + documentReference.getId());
//
//                    // assign the ID of this habit
//                    habitID = documentReference.getId();
//                })
//                .addOnFailureListener(e -> Log.w(TAG, "Error adding document", e));
//    }

    /**
     * This method deletes a specified Habit from the user's Habit Collection
     * It also calls another method to delete all of HabitEvent documents
     * within specified Habit's sub-collection
     */
    public void deleteHabitFromDB() {

        // Delete all HabitEvents within this Habit's HabitEvents sub-collection

        // Get the Habit's eventList
        ArrayList<HabitEvent> eventList = habit.getHabitEvents();
        deleteHabitEventSubCollection(eventList);

        DocumentReference docRef = collectionRef.document(habit.getHabitID());
        docRef.delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Data has been deleted successfully!"))
                .addOnFailureListener(e -> Log.d(TAG, "Data could not be deleted!" + e.toString()));
    }

    /**
     * This method deletes all of the HabitEvent documents within a specified Habit's sub-collection
     */
    public void deleteHabitEventSubCollection(ArrayList<HabitEvent> eventList) {

        // Get sub-collection reference corresponding to the Habit whose events we will delete
        // CollectionReference eventCollectionRef = collectionRef.document(habit.getHabitID()).collection("HabitEvents");
        //db.collection("Users/" +
        //user.getUserID() + "/Habits/" + habit.getHabitID() + "/HabitEvents/");

        // Iterate through all of the events
        for (int i = 0; i < eventList.size(); i++) {

            // Delete the current habitEvent from the db
            HabitEvent habitEvent = eventList.get(i);


            //Log.d(TAG, "Current HabitEventID = " + habitEventID);


            // Get document corresponding to current HabitEvent
            DocumentReference docRef = collectionRef.document(habit.getHabitID())
                    .collection("HabitEvents").document(habitEvent.getEventID());

            // Delete the document
            docRef.delete()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Data has been deleted successfully!"))
                    .addOnFailureListener(e -> Log.d(TAG, "Data could not be deleted!" + e.toString()));
        }
    }

    /**
     * This method replaces a Habit in the logged in user's Habit collection in the database
     */
    public void setHabitInDB() {

        // We will also need to edit the parent name of all of the Habit Events

        long longDate = habit.getStartDate().getTimeInMillis();

        DocumentReference habitDoc = collectionRef.document(habit.getHabitID());
        habitDoc.update(
                "habitName", habit.getHabitName(),
                "weekdays", habit.getWeekdays(),
                "longDate", longDate,
                "reason", habit.getHabitReason(),
                "isPublic", habit.isHabitPublic())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "DocumentSnapshot successfully updated!"))
                .addOnFailureListener(e -> Log.w(TAG, "Error updating document", e));
    }

    @Override
    public void onHabitCallback(Habit habit) {

    }

    @Override
    public void execute(Runnable command) {

    }
}
