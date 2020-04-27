package org.owntracks.android.ui.preferences.connection.dialog;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.widget.PopupMenu;

import org.owntracks.android.App;
import org.owntracks.android.R;
import org.owntracks.android.support.Preferences;
import org.owntracks.android.ui.base.navigator.Navigator;

import java.io.FileOutputStream;
import java.io.InputStream;

import timber.log.Timber;

import static android.app.Activity.RESULT_OK;

public class ConnectionSecurityViewModel extends BaseDialogViewModel {
    private static final int REQUEST_CODE_FILE_CA_CRT = 1;
    private static final int REQUEST_CODE_FILE_CLIENT_CRT = 2;

    private final Navigator navigator;
    private boolean tls;
    private String tlsCaCrtName;
    private String tlsClientCrtName;
    private String tlsClientCrtPassword;
    private boolean tlsClientCrtNameDirty;
    private boolean tlsCaCrtNameDirty;
    private boolean tlsClientCrtPasswortDirty;

    public ConnectionSecurityViewModel(Preferences preferences, Navigator navigator) {
        super(preferences);
        this.navigator = navigator;
    }

    @Override
    public void load() {
        this.tls = preferences.getTls();
        this.tlsCaCrtName = preferences.getTlsCaCrtName();
        this.tlsClientCrtName = preferences.getTlsClientCrtName();
        this.tlsClientCrtPassword = preferences.getTlsClientCrtPassword();
    }

    @Override
    public void save() {
        preferences.setTls(tls);

        if(tlsCaCrtNameDirty)
            preferences.setTlsCaCrt(tlsCaCrtName);

        if(tlsClientCrtNameDirty)
            preferences.setTlsClientCrt(tlsClientCrtName);

        if(tlsClientCrtPasswortDirty)
            preferences.setTlsClientCrtPassword(tlsClientCrtPassword);
    }

    public boolean isTls() {
        return tls;
    }

    private void setTls(boolean tls) {
        this.tls = tls;
        notifyChange();
    }

    public String getTlsCaCrtName() {
        return tlsCaCrtName;
    }

    private void setTlsCaCrtName(String tlsCaCrtName) {
        this.tlsCaCrtName = tlsCaCrtName;
        this.tlsCaCrtNameDirty = true;
        notifyChange();
    }

    public String getTlsClientCrtName() {
        return tlsClientCrtName;
    }

    private void setTlsClientCrtName(String tlsClientCrtName) {
        this.tlsClientCrtName = tlsClientCrtName;
        this.tlsClientCrtNameDirty = true;
        notifyChange();
    }

    public String getTlsClientCrtPassword() {
        return tlsClientCrtPassword;
    }

    public void setTlsClientCrtPassword(String tlsClientCrtPassword) {
        this.tlsClientCrtPassword = tlsClientCrtPassword;
        this.tlsClientCrtPasswortDirty = true;
    }

    public void onTlsCheckedChanged(final View v, boolean isChecked) {
        setTls(isChecked);
    }

    public void onTlsCaCrtNameClick(final View v) {
        PopupMenu popup = new PopupMenu(v.getContext(), v);
        popup.getMenuInflater().inflate(R.menu.picker, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.clear) {
                setTlsCaCrtName(null);
            } else if (item.getItemId() == R.id.select) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");

                try {
                    navigator.startActivityForResult(Intent.createChooser(intent, "Select a file"), REQUEST_CODE_FILE_CA_CRT);
                } catch (android.content.ActivityNotFoundException ex) {
                    // Potentially direct the user to the Market with a Dialog
                }
            }
            return true;
        });
        popup.show();
    }

    public void onTlsClientCrtNameClick(final View v) {

        PopupMenu popup = new PopupMenu(v.getContext(), v);
        popup.getMenuInflater().inflate(R.menu.picker, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.clear) {
                setTlsClientCrtName(null);
            } else if (item.getItemId() == R.id.select) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");

                try {
                    navigator.startActivityForResult(Intent.createChooser(intent, "Select a file"), REQUEST_CODE_FILE_CLIENT_CRT);
                } catch (android.content.ActivityNotFoundException ex) {
                    // Potentially direct the user to the Market with a Dialog
                }
            }
            return true;
        });
        popup.show();
    }




    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(resultCode == RESULT_OK && (requestCode == ConnectionSecurityViewModel.REQUEST_CODE_FILE_CA_CRT || requestCode == ConnectionSecurityViewModel.REQUEST_CODE_FILE_CLIENT_CRT )) {
            Uri uri = data.getData();
            Timber.v("uri:  %s,", uri.toString());
            if (requestCode == ConnectionSecurityViewModel.REQUEST_CODE_FILE_CA_CRT)
                new CaCrtCopyTask().execute(uri);
            else
                new ClientCrtCopyTask().execute(uri);
        }
    }


    private abstract class CopyTask extends AsyncTask<Uri, String, String> {

        @Override
        protected String doInBackground(Uri... params) {
            try {
                Timber.v("CopyTask with URI: %s", params[0]);
                String filename = uriToFilename(params[0]);
                Timber.v("filename for save is: %s", filename);

                InputStream inputStream = App.getContext().getContentResolver().openInputStream(params[0]);
                FileOutputStream outputStream = App.getContext().openFileOutput(filename, Context.MODE_PRIVATE);

                byte[] buffer = new byte[256];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                outputStream.close();
                Timber.v("copied file to private storage: %s", filename);

                return filename;

            } catch (Exception e) {
                e.printStackTrace();
                this.cancel(true);
                return null;
            }
        }
    }

    private class CaCrtCopyTask extends CopyTask {
        @Override
        protected void onPostExecute(String s) {
            Timber.v("crt copied %s",s);
            setTlsCaCrtName(s);
        }

        @Override
        protected void onCancelled(String s) {
            setTlsCaCrtName(null);
            Toast.makeText(App.getContext(), App.getContext().getString(R.string.unableToCopyCertificate), Toast.LENGTH_SHORT).show();
        }
    }

    private class ClientCrtCopyTask extends CopyTask {

        @Override
        protected void onPostExecute(String s) {
            setTlsClientCrtName(s);
        }

        @Override
        protected void onCancelled(String s) {
            setTlsClientCrtName(null);
            Toast.makeText(App.getContext(), App.getContext().getString(R.string.unableToCopyCertificate), Toast.LENGTH_SHORT).show();
        }
    }




    private String uriToFilename(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = App.getContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}

