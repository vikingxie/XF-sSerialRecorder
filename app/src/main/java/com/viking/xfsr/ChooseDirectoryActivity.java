package com.viking.xfsr;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChooseDirectoryActivity extends AppCompatActivity {
    public static final String KEY_DIRECTORY = "key_directory";

    private ListView mDirectoryList = null;
    private List<String> mFilenames = null;
    private ArrayAdapter<String> mListDirectoriesAdapter = null;
    private File[] mFilesInDirectory = null;
    private File mSelectedDirectory = null;
    private TextView mCurrentDirectory = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_directory);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.putExtra(KEY_DIRECTORY, mSelectedDirectory.getAbsolutePath());
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        setupActionBar();

        mCurrentDirectory = (TextView) findViewById(R.id.textViewCurrentDirectory);

        // Init directory list
        mDirectoryList = (ListView) findViewById(R.id.listViewDirectories);
        mDirectoryList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mFilesInDirectory != null && position >= 0 && position < mFilesInDirectory.length) {
                    changeDirectory(mFilesInDirectory[position]);
                }
            }
        });
        mFilenames = new ArrayList<>();
        mListDirectoriesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mFilenames);
        mDirectoryList.setAdapter(mListDirectoriesAdapter);

        String initDirectoryName = getIntent().getStringExtra(KEY_DIRECTORY);
        Toast.makeText(this, initDirectoryName, Toast.LENGTH_LONG).show();
        File initDirectory;
        if (!TextUtils.isEmpty(initDirectoryName) && isValidFile(new File(initDirectoryName))) {
            initDirectory = new File(initDirectoryName);
        } else {
            initDirectory = Environment.getExternalStorageDirectory();
        }
        changeDirectory(initDirectory);
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_choose_directory, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                this.finish();
                break;

            case R.id.action_back:
                // Back to upper directory
                final File parent;
                if (mSelectedDirectory != null && (parent = mSelectedDirectory.getParentFile()) != null) {
                    changeDirectory(parent);
                }
                break;

            case R.id.action_refresh:
                // Refresh current directory
                if (mSelectedDirectory != null) {
                    changeDirectory(mSelectedDirectory);
                }
                break;

            case R.id.action_create:
                //TODO: Create new directory
                break;

            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private boolean isValidFile(final File file) {
        return (file != null && file.isDirectory() && file.canRead() && file.canWrite());
    }

    private void changeDirectory(final File dir) {
        if (dir != null && dir.isDirectory()) {
            final File[] contents = dir.listFiles();
            if (contents != null) {
                int numDirectories = 0;
                for (final File f : contents) {
                    if (f.isDirectory()) {
                        numDirectories++;
                    }
                }
                mFilesInDirectory = new File[numDirectories];
                mFilenames.clear();
                for (int i = 0, counter = 0; i < numDirectories; counter++) {
                    if (contents[counter].isDirectory()) {
                        mFilesInDirectory[i] = contents[counter];
                        mFilenames.add(contents[counter].getName());
                        i++;
                    }
                }
                Arrays.sort(mFilesInDirectory);
                Collections.sort(mFilenames);
                mSelectedDirectory = dir;
                mCurrentDirectory.setText(dir.getAbsolutePath());
                mListDirectoriesAdapter.notifyDataSetChanged();
                //mFileObserver = createFileObserver(dir.getAbsolutePath());
                //mFileObserver.startWatching();
            }
        }
    }

}
