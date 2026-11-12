package com.manu.frosted;

import com.manu.frosted.modules.basehunt.ChunkFinder;
import com.manu.frosted.modules.basehunt.DeepPlayerFinder;
import com.manu.frosted.modules.basehunt.GhostEsp;
import com.manu.frosted.modules.basehunt.LightDebug;
import com.manu.frosted.modules.basehunt.SoundFinder;
import com.manu.frosted.modules.basehunt.SusChunks;
import com.manu.frosted.modules.camera.FrostedFreecam;
import com.manu.frosted.modules.camera.FrostedFreelook;
import com.manu.frosted.modules.combat.AutoJumpReset;
import com.manu.frosted.modules.combat.Autoclicker;
import com.manu.frosted.modules.combat.KeyPearl;
import com.manu.frosted.modules.combat.LegitAim;
import com.manu.frosted.modules.combat.TriggerBot;
import com.manu.frosted.modules.combat.WTap;
import com.manu.frosted.modules.hud.FrostedHud;
import com.manu.frosted.modules.util.DeathCoords;
import com.manu.frosted.modules.util.FrostedUpdater;
import com.manu.frosted.modules.util.RegionMap;
import com.manu.frosted.modules.util.StaffList;
import com.manu.frosted.update.UpdateManager;
import com.manu.frosted.modules.visual.CsKnife;
import com.manu.frosted.modules.visual.CustomCrosshair;
import com.manu.frosted.modules.visual.FrostedAmbience;
import com.manu.frosted.modules.visual.FrostedEffects;
import com.manu.frosted.modules.visual.HitParticles;
import com.manu.frosted.modules.visual.JumpCircles;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrostedAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("frosted");

    // Names must keep the "Frosted" prefix: SnowCategoryMixin frosts headers by it.
    public static final Category COMBAT = new Category("Frosted Combat", Items.DIAMOND_SWORD.getDefaultStack());
    public static final Category BASEHUNT = new Category("Frosted Basehunting", Items.COMPASS.getDefaultStack());
    public static final Category UTIL = new Category("Frosted Util", Items.POWDER_SNOW_BUCKET.getDefaultStack());
    public static final Category VISUAL = new Category("Frosted Visuals", Items.SNOWBALL.getDefaultStack());

    @Override
    public void onInitialize() {
        Modules modules = Modules.get();

        // Basehunting
        modules.add(new SusChunks());
        modules.add(new ChunkFinder());
        modules.add(new DeepPlayerFinder());
        modules.add(new SoundFinder());
        modules.add(new LightDebug());
        modules.add(new GhostEsp());

        // Combat
        modules.add(new KeyPearl());
        modules.add(new WTap());
        modules.add(new AutoJumpReset());
        modules.add(new TriggerBot());
        modules.add(new Autoclicker());
        modules.add(new LegitAim());

        // Camera
        modules.add(new FrostedFreecam());
        modules.add(new FrostedFreelook());

        // Util
        modules.add(new DeathCoords());
        modules.add(new StaffList());
        modules.add(new RegionMap());
        modules.add(new FrostedHud());
        modules.add(new FrostedUpdater());

        // Visuals
        modules.add(new JumpCircles());
        modules.add(new HitParticles());
        modules.add(new CustomCrosshair());
        modules.add(new CsKnife());
        modules.add(new FrostedAmbience());
        modules.add(new FrostedEffects());


        UpdateManager.init();

        LOG.info("frosted loaded");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(BASEHUNT);
        Modules.registerCategory(COMBAT);
        Modules.registerCategory(UTIL);
        Modules.registerCategory(VISUAL);
    }

    @Override
    public String getPackage() {
        return "com.manu.frosted";
    }
}
