package com.tungsten.fcl.ui.download;

import android.content.Context;
import android.text.Html;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.core.text.HtmlCompat;

import com.tungsten.fcl.FCLApplication;
import com.tungsten.fcl.R;
import com.tungsten.fcl.game.LocalizedRemoteModRepository;
import com.tungsten.fcl.setting.Profile;
import com.tungsten.fcl.setting.Profiles;
import com.tungsten.fcl.util.AndroidUtils;
import com.tungsten.fcl.util.ReadTools;
import com.tungsten.fclcore.mod.ModLoaderType;
import com.tungsten.fclcore.mod.ModManager;
import com.tungsten.fclcore.mod.RemoteModRepository;
import com.tungsten.fclcore.mod.curse.CurseForgeRemoteModRepository;
import com.tungsten.fclcore.mod.modrinth.ModrinthRemoteModRepository;
import com.tungsten.fclcore.util.io.IOUtils;
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog;
import com.tungsten.fcllibrary.component.view.FCLUILayout;
import com.tungsten.fclauncher.utils.FCLPath;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModDownloadPage extends DownloadPage {
    private ModManager modManager;

    public ModDownloadPage(Context context, int id, FCLUILayout parent, int resId) {
        super(context, id, parent, resId, null);

        repository = new Repository();

        supportChinese.set(true);
        downloadSources.get().setAll(context.getString(R.string.mods_curseforge), context.getString(R.string.mods_modrinth));
        if (CurseForgeRemoteModRepository.isAvailable())
            downloadSource.set(context.getString(R.string.mods_curseforge));
        else
            downloadSource.set(context.getString(R.string.mods_modrinth));

        create();
        View showIncompatible = findViewById(R.id.show_incompatible);
        showIncompatible.setVisibility(View.VISIBLE);
        showIncompatible.setOnClickListener(v -> {
            try {
                String url = FCLApplication.Prop.getProperty("mod-compatibility-url","https://github.com/hyplant-team/FoldCraftLauncher/tree/doc/compatibility");
                FCLAlertDialog dialog = new FCLAlertDialog(context);
                String local_incompatible_mods = FCLPath.FILES_DIR + "/debug/incompatible_mod_list.html";
                String message;
                if (new File(local_incompatible_mods).exists()) {
                    message = ReadTools.readFileTxt(local_incompatible_mods);
                } else {
                    message = IOUtils.readFullyAsString(context.getAssets().open("incompatible_mod_list.html"));
                }
                dialog.setMessage(Html.fromHtml(message, 0));
                dialog.setPositiveButton(context.getString(R.string.view_incompatible_online), () -> {
                    AndroidUtils.openLink(context, url);
                });
                dialog.setNegativeButton(context.getString(com.tungsten.fcllibrary.R.string.dialog_positive), null);
                dialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void create() {
        super.create();
        binding.modloaderText.setVisibility(View.VISIBLE);
        binding.modloader.setVisibility(View.VISIBLE);
        List<String> modLoaderList = new ArrayList<>();
        modLoaderList.add(getContext().getString(R.string.curse_category_0));
        modLoaderList.add("Forge");
        modLoaderList.add("NeoForge");
        modLoaderList.add("Fabric");
        modLoaderList.add("Quilt");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.item_spinner_auto_tint, modLoaderList);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.modloader.setAdapter(adapter);
        binding.modloader.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        selectedModLoader = null;
                        break;
                    case 1:
                        selectedModLoader = ModLoaderType.FORGE;
                        break;
                    case 2:
                        selectedModLoader = ModLoaderType.NEO_FORGED;
                        break;
                    case 3:
                        selectedModLoader = ModLoaderType.FABRIC;
                        break;
                    case 4:
                        selectedModLoader = ModLoaderType.QUILT;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedModLoader = null;
            }
        });
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        super.loadVersion(profile, version);
        modManager = Profiles.getSelectedProfile().getRepository().getModManager(Profiles.getSelectedVersion());
    }

    public ModManager getModManager() {
        return modManager;
    }

    private class Repository extends LocalizedRemoteModRepository {

        @Override
        protected RemoteModRepository getBackedRemoteModRepository() {
            if (getContext().getString(R.string.mods_modrinth).equals(downloadSource.get())) {
                return ModrinthRemoteModRepository.MODS;
            } else {
                return CurseForgeRemoteModRepository.MODS;
            }
        }

        @Override
        protected SortType getBackedRemoteModRepositorySortOrder() {
            if (getContext().getString(R.string.mods_modrinth).equals(downloadSource.get())) {
                return SortType.NAME;
            } else {
                return SortType.POPULARITY;
            }
        }

        @Override
        public Type getType() {
            return Type.MOD;
        }
    }

    @Override
    protected String getLocalizedCategory(String category) {
        if (getContext().getString(R.string.mods_modrinth).equals(downloadSource.get())) {
            return AndroidUtils.getLocalizedText(getContext(), "modrinth_category_" + category.replaceAll("-", "_"));
        } else {
            return AndroidUtils.getLocalizedText(getContext(), "curse_category_" + category);
        }
    }

    @Override
    protected String getLocalizedOfficialPage() {
        return downloadSource.get();
    }

}
