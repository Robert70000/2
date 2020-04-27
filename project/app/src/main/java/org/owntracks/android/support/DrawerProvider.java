package org.owntracks.android.support;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.SecondarySwitchDrawerItem;

import org.owntracks.android.R;
import org.owntracks.android.services.BackgroundService;
import org.owntracks.android.ui.base.BaseActivity;
import org.owntracks.android.ui.contacts.ContactsActivity;
import org.owntracks.android.ui.map.MapActivity;
import org.owntracks.android.ui.preferences.PreferencesActivity;
import org.owntracks.android.ui.regions.RegionsActivity;
import org.owntracks.android.ui.status.StatusActivity;

public class DrawerProvider  {
    private static final int COLOR_ICON_PRIMARY = R.color.md_light_primary_icon;
    private static final int COLOR_ICON_PRIMARY_ACTIVE = R.color.md_blue_600;
    private static final int COLOR_ICON_SECONDARY = R.color.md_light_secondary;
    private static final int COLOR_ICON_SECONDARY_ACTIVE = COLOR_ICON_PRIMARY_ACTIVE;

    private static final int EXIT_OPERATION_ID = 88296;

    private final AppCompatActivity activity;

    public DrawerProvider(AppCompatActivity activity) {
        this.activity = activity;
    }

    private AppCompatActivity getActivity() {
        return activity;
    }

    private PrimaryDrawerItem drawerItemForClass(AppCompatActivity activeActivity, Class<?> targetActivityClass, @StringRes int targetActivityTitleRessource, @DrawableRes int iconResource) {

        return new PrimaryDrawerItem()
                .withName(activeActivity.getString(targetActivityTitleRessource))
                .withSelectable(false)
                .withSelectedTextColorRes(COLOR_ICON_PRIMARY_ACTIVE)
                .withIcon(iconResource)
                .withIconColorRes(COLOR_ICON_PRIMARY)
                .withIconTintingEnabled(true)
                .withSelectedIconColorRes(COLOR_ICON_PRIMARY_ACTIVE)
                .withTag(targetActivityClass)
                .withIdentifier(targetActivityClass.hashCode());

    }
    private SecondaryDrawerItem secondaryDrawerItemForClass(AppCompatActivity activeActivity, Class<?> targetActivityClass, @StringRes int targetActivityTitleRessource, @DrawableRes int iconResource) {

        SecondaryDrawerItem sdi = new SecondaryDrawerItem();
        sdi.withName(activeActivity.getString(targetActivityTitleRessource));
        sdi.withIcon(iconResource);
        sdi.withIconColorRes(COLOR_ICON_SECONDARY);
        sdi.withSelectedIconColorRes(COLOR_ICON_SECONDARY_ACTIVE);
        sdi.withIconTintingEnabled(true);
        // The exit operation has no target class
        if (targetActivityClass != null) {
            sdi.withTag(targetActivityClass);
            sdi.withIdentifier(targetActivityClass.hashCode());
        } else {
            sdi.withIdentifier(EXIT_OPERATION_ID);
        }
        sdi.withSelectable(false);

        return sdi;

    }

    public void attach(@NonNull Toolbar toolbar) {

        DrawerBuilder d = new DrawerBuilder();

        d.withActivity(activity);
        d.withToolbar(toolbar);
        d.withStickyFooterShadow(false);
        d.withStickyFooterDivider(true);

        // Add the top drawer items
        d.addDrawerItems(
                drawerItemForClass(activity, MapActivity.class, R.string.title_activity_map, R.drawable.ic_layers_black_24dp),
                drawerItemForClass(activity, ContactsActivity.class, R.string.title_activity_contacts, R.drawable.ic_supervisor_account_black_24dp),
                drawerItemForClass(activity, RegionsActivity.class, R.string.title_activity_regions, R.drawable.ic_adjust_black_24dp));

        // Add the lower drawer items
        d.addStickyDrawerItems(
                secondaryDrawerItemForClass(activity, StatusActivity.class, R.string.title_activity_status, R.drawable.ic_info_black_24dp),
                secondaryDrawerItemForClass(activity, PreferencesActivity.class, R.string.title_activity_preferences, R.drawable.ic_settings_black_36dp),
                secondaryDrawerItemForClass(activity, null, R.string.title_exit, R.drawable.ic_poweroff_black_36dp)
        );



        d.withOnDrawerItemClickListener((view, position, drawerItem)  -> {
            if (drawerItem == null)
                return false;

            if (drawerItem instanceof SecondarySwitchDrawerItem)
                return true;

            // Finish when exit app drawer option selected
            if (drawerItem.getIdentifier() == EXIT_OPERATION_ID) {
                // Stop the background service
                activity.stopService((new Intent(activity, BackgroundService.class)));
                // Finish the activity
                activity.finishAffinity();
                return true;
            }

            Class<BaseActivity> targetclass = (Class<BaseActivity>) drawerItem.getTag();

            if (activity.getClass() == targetclass) {
                return false;
            }

            startActivity(targetclass);

            return false; // return false to enable withCloseOnClick
        });

        d.build();
    }


    private void startActivity(@NonNull Class<? extends Activity> activityClass) {
        Activity activity = getActivity();
        Intent intent = new Intent(activity, activityClass);
        activity.startActivity(intent);
    }

}


