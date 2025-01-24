package com.tungsten.fcl.ui.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.tungsten.fcl.FCLApplication;
import com.tungsten.fcl.R;
import com.tungsten.fcl.game.TexturesLoader;
import com.tungsten.fcl.setting.Accounts;
import com.tungsten.fcl.util.AndroidUtils;
import com.tungsten.fcl.util.ReadTools;
import com.tungsten.fclcore.auth.Account;
import com.tungsten.fclcore.fakefx.beans.property.ObjectProperty;
import com.tungsten.fclcore.fakefx.beans.property.SimpleObjectProperty;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fclcore.util.io.HttpRequest;
import com.tungsten.fclcore.util.io.NetworkUtils;
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog;
import com.tungsten.fcllibrary.component.theme.ThemeEngine;
import com.tungsten.fcllibrary.component.ui.FCLCommonUI;
import com.tungsten.fcllibrary.component.view.FCLButton;
import com.tungsten.fcllibrary.component.view.FCLTextView;
import com.tungsten.fcllibrary.component.view.FCLUILayout;
import com.tungsten.fcllibrary.skin.SkinCanvas;
import com.tungsten.fcllibrary.skin.SkinRenderer;
import com.tungsten.fcllibrary.util.LocaleUtils;
import com.tungsten.fclauncher.utils.FCLPath;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MainUI extends FCLCommonUI implements View.OnClickListener {

    public static final String ANNOUNCEMENT_URL = FCLApplication.Prop.getProperty("announcement-url","https://raw.githubusercontent.com/hyplant-team/FoldCraftLauncher/refs/heads/doc/announcement/latest.json");

    private LinearLayoutCompat announcementContainer;
    private LinearLayoutCompat announcementLayout;
    private FCLTextView title;
    private FCLTextView announcementView;
    private FCLTextView date;
    private FCLButton hide;
    private Announcement announcement = null;
    private boolean isChecking = false;

    private RelativeLayout skinContainer;
    private SkinCanvas skinCanvas;
    private SkinRenderer renderer;

    private ObjectProperty<Account> currentAccount;

    public MainUI(Context context, FCLUILayout parent, int id) {
        super(context, parent, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        announcementContainer = findViewById(R.id.announcement_container);
        announcementLayout = findViewById(R.id.announcement_layout);
        title = findViewById(R.id.title);
        announcementView = findViewById(R.id.announcement);
        date = findViewById(R.id.date);
        hide = findViewById(R.id.hide);
        ThemeEngine.getInstance().registerEvent(announcementLayout, () -> announcementLayout.getBackground().setTint(ThemeEngine.getInstance().getTheme().getColor()));
        hide.setOnClickListener(this);

        skinContainer = findViewById(R.id.skin_container);
        renderer = new SkinRenderer(getContext());
        ViewGroup.LayoutParams layoutParamsSkin = skinContainer.getLayoutParams();
        layoutParamsSkin.width = (int) (((View) skinContainer.getParent().getParent()).getMeasuredWidth() * 0.5f);
        layoutParamsSkin.height = (int) Math.min(((View) skinContainer.getParent().getParent()).getMeasuredWidth() * 0.5f, ((View) skinContainer.getParent().getParent()).getMeasuredHeight());
        skinContainer.setLayoutParams(layoutParamsSkin);

        setupSkinDisplay();
    }

    @Override
    public void onStart() {
        super.onStart();
        skinContainer.setVisibility(View.GONE);
        announcementContainer.setVisibility(View.GONE);
        checkAnnouncement();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (skinCanvas != null) {
            skinCanvas.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isShowing() && skinCanvas != null) {
            skinCanvas.onResume();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (skinCanvas != null) {
            skinCanvas.onPause();
        }
        skinContainer.removeView(skinCanvas);
    }

    @Override
    public Task<?> refresh(Object... param) {
        return Task.runAsync(() -> {

        });
    }

    private void checkAnnouncement() {
        if(FCLApplication.Prop.getProperty("enable-announcement","true").equals("true")){
            isChecking = true;
            AtomicReference<String> remoteDataRef = new AtomicReference<>();
            AtomicReference<Announcement> announcementDataRef = new AtomicReference<>();
            title.setText(getContext().getString(R.string.announcement));
            announcementView.setText(getContext().getString(R.string.announcement_loading));
            date.setText(new String(ANNOUNCEMENT_URL));
            announcementContainer.setVisibility(View.VISIBLE);
            checkSkinDisplay();
            CompletableFuture<Announcement> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String remoteData;
                    String local_announcement = FCLPath.FILES_DIR + "/debug/announcement.json";
                    if (new File(local_announcement).exists()) {
                        remoteData = ReadTools.readFileTxt(local_announcement);
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "DEBUG announcement.json", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        remoteData = NetworkUtils.doGet(NetworkUtils.toURL(ANNOUNCEMENT_URL));
                    }
                    remoteDataRef.set(remoteData);
                }catch (Exception e) {
                    Logging.LOG.log(Level.WARNING, "Unable to load online announcement", e);
                    return new Announcement(
                        -1, true, false, -1, -1, new ArrayList<>(),
                        new ArrayList<>(Collections.singletonList(new Announcement.Content(null, getContext().getString(R.string.announcement_error_network)))),
                        new String(ANNOUNCEMENT_URL),
                        new ArrayList<>(Collections.singletonList(new Announcement.Content(null, getContext().getString(R.string.announcement_error_network_content) + "\n" + ANNOUNCEMENT_URL)))
                    );
                }
                try {
                    Announcement announcementData = new Gson().fromJson(remoteDataRef.get(), Announcement.class);
                    announcementDataRef.set(announcementData);
                }catch (Exception e) {
                    Logging.LOG.log(Level.WARNING, "Failed to process JSON file", e);
                    return new Announcement(
                        -1, true, false, -1, -1, new ArrayList<>(),
                        new ArrayList<>(Collections.singletonList(new Announcement.Content(null, getContext().getString(R.string.announcement_error_format)))),
                        new String(ANNOUNCEMENT_URL),
                        new ArrayList<>(Collections.singletonList(new Announcement.Content(null, getContext().getString(R.string.announcement_error_format_content) + "\n" + remoteDataRef.get())))
                    );
                }
                return announcementDataRef.get();
            });
            future.thenAccept(announcement -> new Handler(Looper.getMainLooper()).post(() -> {
                this.announcement = announcement;
                try {
                    if (!announcement.shouldDisplay(getContext())) {
                        announcementContainer.setVisibility(View.GONE);
                        checkSkinDisplay();
                        isChecking = false;
                        return;
                    }
                    title.setText(this.announcement.getDisplayTitle(getContext()));
                    announcementView.setText(this.announcement.getDisplayContent(getContext()));
                    date.setText(this.announcement.getDate());
                }catch(Exception e) {
                    Logging.LOG.log(Level.WARNING, "Failed to process announcement data", e);
                    title.setText(getContext().getString(R.string.announcement_error_data));
                    announcementView.setText(ANNOUNCEMENT_URL);
                    date.setText(getContext().getString(R.string.announcement_error_data_content) + "\n" + remoteDataRef.get());
                }
                isChecking = false;
            }));
        } else {
            checkSkinDisplay();
        }
    }

    private void hideAnnouncement() {
        announcementContainer.setVisibility(View.GONE);
        if (announcement != null) {
            announcement.hide(getContext());
        }
        checkSkinDisplay();
    }

    private void setupSkinDisplay() {
        currentAccount = new SimpleObjectProperty<Account>() {

            @Override
            protected void invalidated() {
                Account account = get();
                renderer.textureProperty().unbind();
                if (account == null) {
                    renderer.updateTexture(BitmapFactory.decodeStream(MainUI.class.getResourceAsStream("/assets/img/alex.png")), null);
                } else {
                    renderer.textureProperty().bind(TexturesLoader.textureBinding(account));
                }
            }
        };
        currentAccount.bind(Accounts.selectedAccountProperty());
    }

    private void checkSkinDisplay() {
        if (isShowing() && !ThemeEngine.getInstance().theme.isCloseSkinModel() && announcementContainer.getVisibility() == View.GONE) {
            if (skinCanvas == null) {
                skinCanvas = new SkinCanvas(getContext());
                skinCanvas.setRenderer(renderer, 5f);
            } else {
                skinCanvas.onResume();
                skinContainer.removeView(skinCanvas);
                renderer.updateTexture(renderer.getTexture()[0], renderer.getTexture()[1]);
            }
            skinContainer.addView(skinCanvas);
            skinContainer.setVisibility(View.VISIBLE);
        } else {
            if (skinCanvas != null) {
                skinCanvas.onPause();
                skinContainer.removeView(skinCanvas);
                skinContainer.setVisibility(View.GONE);
            }
        }
    }

    public void refreshSkin(Account account) {
        Schedulers.androidUIThread().execute(() -> {
            if (currentAccount.get() == account) {
                renderer.textureProperty().unbind();
                renderer.textureProperty().bind(TexturesLoader.textureBinding(currentAccount.get()));
            }
        });
    }

    public void onBackPressed() {
        FCLAlertDialog.Builder builder = new FCLAlertDialog.Builder(getContext());
        builder.setAlertLevel(FCLAlertDialog.AlertLevel.INFO);
        builder.setCancelable(false);
        builder.setMessage(getContext().getString(R.string.menu_settings_force_exit_msg));
        builder.setPositiveButton(getContext().getString(com.tungsten.fcllibrary.R.string.dialog_negative), null);
        builder.setNegativeButton(getContext().getString(com.tungsten.fcllibrary.R.string.dialog_positive), () -> {
            getActivity().finish();
            System.exit(0);
        });
        builder.create().show();
    }

    @Override
    public void onClick(View view) {
        if (view == hide) {
            if (announcement != null && (isChecking || announcement.isSignificant())) {
                FCLAlertDialog.Builder builder = new FCLAlertDialog.Builder(getContext());
                builder.setAlertLevel(FCLAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(true);
                builder.setMessage(getContext().getString(R.string.announcement_significant));
                builder.setPositiveButton(null, null);
                builder.setNegativeButton(getContext().getString(com.tungsten.fcllibrary.R.string.dialog_positive), null);
                builder.create().show();
            } else {
                hideAnnouncement();
            }
        }
    }
}