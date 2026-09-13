package com.example.cajadomod;
import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class DestructionStaffMod implements ModInitializer {
    public static final String MOD_ID = "cajadomod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Item DESTRUCTION_STAFF = new DestructionStaffItem(new Item.Settings().maxCount(1));
    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "cajado_destruicao"), DESTRUCTION_STAFF);
        LOGGER.info("[Cajado da Destruição] Cajado com sistema de carga registrado!");
    }
}
