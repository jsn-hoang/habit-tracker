package com.example.gittersandsittersdatabase;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for the initial HabitTracker login screen
 */
public class MainActivity extends AppCompatActivity {

    // Declare variables to be referenced

    private TextView register;
    private EditText editTextEmail, editTextPassword;
    private Button signIn;
    private FirebaseAuth mAuth; // The entry point of the Firebase Authentication SDK
    FirebaseFirestore fStore;   // The entry point for all Cloud Firestore operations
    private ProgressBar progressBar;
    private String userID;
    private boolean isSignedIn;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        isSignedIn = false;
        editTextEmail = findViewById(R.id.login_emailAddress);
        editTextPassword = findViewById(R.id.login_password);
        progressBar = findViewById(R.id.progress_bar);
        // Get an instance of Firebase Authentication SDK
        mAuth = FirebaseAuth.getInstance();
        // Get an instance of the Firebase Firestore
        fStore = FirebaseFirestore.getInstance();

       // Register button to switch to RegisterActivity
        final Button registerButton = findViewById(R.id.register_button);
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, Register.class));
            }
        });

        // Login button to execute logic for attempted login
        final Button loginButton = findViewById(R.id.login_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                executeLoginAttempt();
            }
        });
    }

    /**
     * This method handles the logic for an attempted user login.
     * If there is a successful login, a "new" User object is created
     * with the logged in user's userID fields as attributes
     * All of the user's existing Habits, etc are fetched from
     * Firebase as well.
     */
    public void executeLoginAttempt() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        // Check to see if email field is empty
        if (email.isEmpty()) {
            editTextEmail.setError("Email is required");
            editTextEmail.requestFocus();
            return;
        }

        // Check to see if email format is correct
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError("Please enter a valid email");
            editTextEmail.requestFocus();
            return;
        }

        // Check to see if password field is empty
        if (password.isEmpty()) {
            editTextPassword.setError("Password is required!");
            editTextPassword.requestFocus();
            return;
        }

        // Check to see if password length is less than 6
        if (password.length() < 6) {
            editTextPassword.setError("Min password length is 6 characters!");
            editTextPassword.requestFocus();
            return;
        }

        // Initialize progress bar
        progressBar.setVisibility(View.VISIBLE);

        // Attempt to sign in user with provided email and password
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {

                // if sign in is successful
                if (task.isSuccessful()) {
                    FirebaseUser fUser = FirebaseAuth.getInstance().getCurrentUser();

                    // if email has previously been verified
                    if (fUser.isEmailVerified()) {

                        // Get the string that uniquely identifies this user in the Firestore
                        userID = mAuth.getCurrentUser().getUid();
                        // Get document reference for document with this unique UserID
                        DocumentReference docRef = fStore.collection("Users").document(userID);
                        // This listener reads the document referenced by docRef
                        docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                if (task.isSuccessful()) {
                                    DocumentSnapshot document = task.getResult();
                                    // if document matching userID exists
                                    if (document.exists()) {

                                        Log.d("TAG", "DocumentSnapshot data: " + document.getData());

                                        /** Create "new" User from document data
                                         */
                                        // Get data from userID document
                                        String username = (String) document.getData().get("userName");
                                        String email = (String) document.getData().get("email");
                                        user = new User(userID, username, email);

                                        /** Get all Firebase data corresponding to the logged in user
                                         */
                                        getUserData();

                                    } else {
                                        Log.d("TAG", "No such document");
                                    }
                                } else {
                                    Log.d("TAG", "get failed with ", task.getException());
                                }
                            }
                        });
                    } else {
                        fUser.sendEmailVerification();
                        Toast.makeText(MainActivity.this, "Check your email to verify your account!", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Failed to login! Please check your credentials", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** This method is a caller to fetch all Firebase data corresponding to the logged in user
     */
    public void getUserData() {
        // Get User Habits
        getUserHabits();
        // Get User Followers
        getUserFollowers();
        // Get User Following
        getUserFollowing();
    }

    /**
     * This method searches the logged in user's Habit Collection
     * All of the documents within the collection are converted
     * to Habit objects and added to the user's habitList
     */
    public void getUserHabits() {
        // Get a reference to the logged in user's Habit collection
        CollectionReference habitCollectionReference =
                fStore.collection("Users").document(userID).collection("Habits");

        // Attempt to get all documents from the habitCollectionReference
        habitCollectionReference.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {

                // If Habit documents exist
                if (task.isSuccessful()) {
                    // For document in collection
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Log.d(TAG, document.getId() + " => " + document.getData());
                        // Set habitName as document ID
                        String habitName = document.getId();

                        // Get remaining Habit attributes from document
                        String reason = (String) document.getData().get("reason");
                        boolean isPublic = (boolean) document.getData().get("isPublic");


                        ArrayList<Integer> weekdays = (ArrayList<Integer>) document.get("weekdays");

                        // Initialize weekdays arraylist
                        //ArrayList<Integer> weekdays = new ArrayList<>();

                        // Convert long object to type Calendar
                        long longDate = (long) document.getData().get("longDate");
                        Calendar startDate = Calendar.getInstance();
                        startDate.setTimeInMillis(longDate);
                        //Calendar startDate = Calendar.getInstance();

                        Habit habit = new Habit(habitName, weekdays, startDate, reason, isPublic);
                        // Add Habit to logged in user
                        user.addUserHabit(habit);
                    }
                    // Send the user to HabitActivity
                    Intent intent = new Intent(MainActivity.this, HabitActivity.class);
                    intent.putExtra("user", user);
                    startActivity(intent);
                } else {
                    Log.d(TAG, "Error getting documents: ", task.getException());
                }
            }
        });
    }

    public void getUserFollowers() {
    };
    public void getUserFollowing() {
    };
}

