package my.edu.utar.taskmate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public abstract class BaseActivity extends AppCompatActivity {

    @LayoutRes
    protected abstract int getLayoutResourceId();

    // Each Activity will override this to highlight the correct menu item
    protected abstract int getBottomNavMenuId();

    // Make SharedPreferences available to child activities
    protected SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Initialize SharedPreferences first
        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        // Apply saved dark mode BEFORE inflating layout
        boolean dark = prefs.getBoolean("darkMode", false);
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);

        // ✅ Inflate the layout so child activities can safely use findViewById
        setContentView(getLayoutResourceId());

        // Skip bottom nav setup if no menu is used
        if (getBottomNavMenuId() == 0) return;

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav == null) {
            bottomNav = findViewById(R.id.bottomNavigationView);
        }

        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_home) {
                        if (!(BaseActivity.this instanceof MainActivity)) {
                            startActivity(new Intent(BaseActivity.this, MainActivity.class));
                            overridePendingTransition(0, 0);
                            finish();
                        }
                        return true;
                    } else if (itemId == R.id.nav_post) {
                        if (!(BaseActivity.this instanceof PostTaskActivity)) {
                            startActivity(new Intent(BaseActivity.this, PostTaskActivity.class));
                            overridePendingTransition(0, 0);
                            finish();
                        }
                        return true;
                    } else if (itemId == R.id.nav_tasks) {
                        if (!(BaseActivity.this instanceof MyTasksActivity)) {
                            startActivity(new Intent(BaseActivity.this, MyTasksActivity.class));
                            overridePendingTransition(0, 0);
                            finish();
                        }
                        return true;
                    } else if (itemId == R.id.nav_chatbot) {
                        if (!(BaseActivity.this instanceof ChatBotActivity)) {
                            startActivity(new Intent(BaseActivity.this, ChatBotActivity.class));
                            overridePendingTransition(0, 0);
                            finish();
                        }
                        return true;
                    } else if (itemId == R.id.nav_profile) {
                        if (!(BaseActivity.this instanceof ProfileActivity)) {
                            startActivity(new Intent(BaseActivity.this, ProfileActivity.class));
                            overridePendingTransition(0, 0);
                            finish();
                        }
                        return true;
                    }
                    return false;
                }
            });

            // Highlight the active menu item
            bottomNav.setSelectedItemId(getBottomNavMenuId());
        }
    }

    // Unified back navigation to MainActivity to avoid app exit on back
    protected void navigateBackToMain() {
        Intent intent = new Intent(BaseActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
