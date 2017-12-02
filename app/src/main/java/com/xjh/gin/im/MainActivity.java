package com.xjh.gin.im;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private Button btnfilemode,btnstreammode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initEvent();
    }

    private void initView() {
        btnfilemode = findViewById(R.id.mBtnFileMode);
        btnstreammode=findViewById(R.id.mBtnStreamMode);
    }

    private void initEvent() {
        btnfilemode.setOnClickListener(this);
        btnstreammode.setOnClickListener(this);
    }

    public void fileMode() {
        startActivity(new Intent(this, FileActivity.class));
    }

    public void streamMode(){
        startActivity(new Intent(this,StreamActivity.class));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.mBtnFileMode:
                fileMode();
                break;

            case R.id.mBtnStreamMode:
                streamMode();
                break;
        }
    }
}
