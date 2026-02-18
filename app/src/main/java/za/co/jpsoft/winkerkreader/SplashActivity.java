package za.co.jpsoft.winkerkreader;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;


/**
 * Created by Pieter Grobler on 30/08/2017.
 */

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, MainActivity2.class);
        startActivity(intent);
        finish();
    }
}
