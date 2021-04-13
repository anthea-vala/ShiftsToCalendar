package com.week1.addshifts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    Button addToCalanderBtn;
    EditText shiftsInput;
    String shifts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // link variables to views
        addToCalanderBtn = findViewById(R.id.homeButton);
        shiftsInput = findViewById(R.id.shifts);

        //check and request for calender read and write access
        if (checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_CALENDAR}, 1);
            return;
        }
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CALENDAR},1);
            return;
        }

        addToCalanderBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //convert user input to string
                shifts = shiftsInput.getText().toString();

                //check if input is empty or not a valid ROSS text (ie. doesn't start with "Your shifts"
                if (shifts.isEmpty())
                    Toast.makeText(getApplicationContext(), "Please enter your shifts", Toast.LENGTH_SHORT).show();
                else if (shifts.substring(0, 11).equals("Your shifts")) {
                    //if input is valid ROSS test, clean up data into an array that can be easily inputted to calendar
                    String[] shiftsArray = cleanData(shifts);

                    //add events into calendar
                    addEvent(shiftsArray);

                    //display new page to verify that shifts are being added to calendar
                    Intent intent = new Intent(getApplicationContext(), ShiftsAdded.class);
                    startActivity(intent);
                } else
                    Toast.makeText(getApplicationContext(), "Please enter your full ROSS text!", Toast.LENGTH_SHORT).show();

            }
        });

    }

    public String[] cleanData(String text) {
        /* Example text for context:
        "Your shifts
        Sun, 4th Apr 09:45-17:00, 30min break
        Wed, 7th Apr 18:00-19:00, No break
        Sun, 11th Apr 09:45-17:00, 30min break
        Sun, 18th Apr 09:45-17:00, 30min break
        Sun, 25th Apr PHOFF (2.75h), No break
        Sun, 25th Apr 13:00-17:00, No break
        Access account at rosters.in.telstra.com.au"
        */

        //remove extra irrelevant writing at start and end of text message
        String newText = text.replaceAll("Your shifts", "");
        newText = newText.replaceAll("Access account at rosters.in.telstra.com.au", "");

        //split into day, date and time, and break length
        String[] dates = newText.split(",");
        String newdates = "";

        //remove everything except date and time
        for (int i = 1; i < dates.length; i += 2) {
            newdates += dates[i];
        }

        //remove extra space at beginning of string
        newdates = newdates.substring(1,newdates.length());
        //remove space from any public holiday PHOFF entries
        newdates = newdates.replaceAll("PHOFF ", "PHOFF");
        //split into day month and time
        String [] dayMonthTime = newdates.split(" ");

        return dayMonthTime;
    }

    public void addEvent(String[] shiftsArray){
        long calID = getCalendarId(getApplicationContext());
        long startMillis = 0;
        long endMillis = 0;
        Calendar beginTime = Calendar.getInstance();
        int day = 0, month = 0, startHour = 0, startMinute = 0, endHour = 0, endMinute= 0;
        String timeString = "";

        //get month from first roster date entry
        SimpleDateFormat format = new SimpleDateFormat("MMM");
        try {
            Date date = format.parse(shiftsArray[1]);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            month = cal.get(Calendar.MONTH);
        } catch (java.text.ParseException e){
            Log.i("YEP", "fail");
        };

        //add each shift to calendar
        for(int i = 0; i < shiftsArray.length; i+=3){
            //check if day of month is one or two digits and add to day variable
            if (shiftsArray[i].substring(0,2).matches("\\d+(?:\\.\\d+)?"))
                day = Integer.parseInt(shiftsArray[i].substring(0,2));
            else
                day = Integer.parseInt(shiftsArray[i].substring(0,1));

            //get shift start time and end time from text and check for PHOFF
            if(!shiftsArray[i+2].substring(0,1).matches("P")) {
                startHour = Integer.parseInt(shiftsArray[i + 2].substring(0, 2));
                startMinute = Integer.parseInt(shiftsArray[i + 2].substring(3, 5));
                endHour = Integer.parseInt(shiftsArray[i + 2].substring(6, 8));
                endMinute = Integer.parseInt(shiftsArray[i + 2].substring(9, 11));

                //check if shift is in morning and has leading zero and store accordingly
                if (shiftsArray[i + 2].substring(0, 1).matches("0"))
                    timeString = shiftsArray[i + 2].substring(1, 11);
                else
                    timeString = shiftsArray[i + 2].substring(0, 11);

                beginTime.set(beginTime.get(Calendar.YEAR), month, day, startHour, startMinute);
                startMillis = beginTime.getTimeInMillis();
                Calendar endTime = Calendar.getInstance();
                endTime.set(beginTime.get(Calendar.YEAR), month, day, endHour, endMinute);
                endMillis = endTime.getTimeInMillis();

                Log.i("YEP", "" + day + month + beginTime.get(Calendar.YEAR) + startHour + startMinute + endHour + endMinute);

                ContentResolver cr = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.DTSTART, startMillis);
                values.put(CalendarContract.Events.DTEND, endMillis);
                values.put(CalendarContract.Events.TITLE, "Work " + timeString);
                values.put(CalendarContract.Events.CALENDAR_ID, calID);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, "Australia/Melbourne");

                //add to calender
                if (checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                    Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
                    long eventID = Long.parseLong(uri.getLastPathSegment());
                    Log.i("YEP", eventID + "");
                }
            }

        }

    }

    private int getCalendarId(Context context) {
        //get primary google account calendar ID
        Cursor cursor = null;
        ContentResolver contentResolver = context.getContentResolver();
        Uri calendars = CalendarContract.Calendars.CONTENT_URI;
        String[] EVENT_PROJECTION = new String[]{
                CalendarContract.Calendars._ID,                           // 0
                CalendarContract.Calendars.ACCOUNT_NAME,                  // 1
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,         // 2
                CalendarContract.Calendars.OWNER_ACCOUNT,                 // 3
                CalendarContract.Calendars.IS_PRIMARY                     // 4
        };

        int PROJECTION_ID_INDEX = 0;
        int PROJECTION_ACCOUNT_NAME_INDEX = 1;
        int PROJECTION_DISPLAY_NAME_INDEX = 2;
        int PROJECTION_OWNER_ACCOUNT_INDEX = 3;
        int PROJECTION_VISIBLE = 4;

        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {

        }
        cursor = contentResolver.query(calendars, EVENT_PROJECTION, null, null, null);
        if (cursor.moveToFirst()) {
            String calName;
            long calId = 0;
            String visible;
            do {
                calName = cursor.getString(PROJECTION_DISPLAY_NAME_INDEX);
                calId = cursor.getLong(PROJECTION_ID_INDEX);
                visible = cursor.getString(PROJECTION_VISIBLE);
                if(visible.equals("1")){
                    return (int)calId;
                }
                Log.e("Calendar Id : ", "" + calId + " : " + calName + " : " + visible);
            } while (cursor.moveToNext());

            return (int)calId;
        }
        return 1;
    }
}
