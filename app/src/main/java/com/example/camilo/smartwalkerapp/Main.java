package com.example.camilo.smartwalkerapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;


/**
 * Created by Camilo on 2/23/2016.
 */
public class Main extends AppCompatActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_screen);
    }

    public void enterApp(View v){
        switch(v.getId()){
            case R.id.centralNode:
                final Intent i = new Intent(Main.this, DeviceScanActivity.class);
                startActivity(i);
                break;
            case R.id.node:
                final Intent j = new Intent(Main.this, Node.class);
                startActivity(j);
        }
    }
}
