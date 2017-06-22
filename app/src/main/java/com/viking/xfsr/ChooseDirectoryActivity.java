package com.viking.xfsr;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
    private FileObserver mFileObserver = null;

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
                refreshDirectory();
                break;

            case R.id.action_create:
                openNewFolderDialog();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mFileObserver != null) {
            mFileObserver.stopWatching();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFileObserver != null) {
            mFileObserver.startWatching();
        }
    }

    private boolean isValidFile(final File file) {
        return (file != null && file.isDirectory() && file.canRead() && file.canWrite());
    }

    private void refreshDirectory() {
        if (mSelectedDirectory != null) {
            changeDirectory(mSelectedDirectory);
        }
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
                mFileObserver = createFileObserver(dir.getAbsolutePath());
                mFileObserver.startWatching();
            }
        }
    }

    private void openNewFolderDialog() {
        @SuppressLint("InflateParams")
        final View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_new_folder, null);
        //final TextView msgView = (TextView) dialogView.findViewById(R.id.msgText);
        final EditText editText = (EditText) dialogView.findViewById(R.id.editText);
        editText.setText("");
        //msgView.setText(getString(R.string.create));

        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.create)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                dialog.dismiss();
                            }
                        })
                .setPositiveButton(R.string.confirm,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                dialog.dismiss();
                                final int msg = createFolder(editText.getText().toString());
                                Toast.makeText(ChooseDirectoryActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        })
                .show();

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(editText.getText().length() != 0);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence charSequence, final int i, final int i2, final int i3) {

            }

            @Override
            public void onTextChanged(final CharSequence charSequence, final int i, final int i2, final int i3) {
                final boolean textNotEmpty = charSequence.length() != 0;
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(textNotEmpty);
                //msgView.setText(getString(R.string.create_folder_msg, charSequence.toString()));
            }

            @Override
            public void afterTextChanged(final Editable editable) {

            }
        });
    }

    private int createFolder(String newDirectoryName) {
        if (newDirectoryName != null && mSelectedDirectory != null
                && mSelectedDirectory.canWrite()) {
            final File newDir = new File(mSelectedDirectory, newDirectoryName);
            if (newDir.exists()) {
                return R.string.folder_exists;
            } else {
                final boolean result = newDir.mkdir();
                if (result) {
                    changeDirectory(newDir);
                    return R.string.create_folder_success;
                } else {
                    return R.string.create_folder_fail;
                }
            }
        } else if (mSelectedDirectory != null && !mSelectedDirectory.canWrite()) {
            return R.string.no_write_access;
        } else {
            return R.string.create_folder_fail;
        }
    }

    private FileObserver createFileObserver(final String path) {
        return new FileObserver(path, FileObserver.CREATE | FileObserver.DELETE
                | FileObserver.MOVED_FROM | FileObserver.MOVED_TO) {

            @Override
            public void onEvent(final int event, final String path) {
                ChooseDirectoryActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshDirectory();
                    }
                });
            }
        };
    }

}
