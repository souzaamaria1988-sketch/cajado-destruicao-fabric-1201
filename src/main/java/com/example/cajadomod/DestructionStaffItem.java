package com.example.cajadomod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

public class DestructionStaffItem extends Item {

    public static final int MODE_FIRE = 0;
    public static final int MODE_LIGHTNING = 1;
    public static final int MODE_VOID = 2;
    public static final int MODE_TSUNAMI = 3;
    public static final int MODE_METEOR = 4;
    public static final int MODE_PLAGUE = 5;
    public static final int MODE_VORTEX = 6;
    public static final int MODE_COUNT = 7;
    public static final int FULL_CHARGE_TICKS = 60;
    public static final int MAX_CHARGE_TICKS = 120;

    public DestructionStaffItem(Settings settings) { super(settings); }

    private float getCharge(int usedTicks) {
        return Math.max(0.15f, Math.min(2.0f, usedTicks / (float) FULL_CHARGE_TICKS));
    }

    @Override
    public int getMaxUseTime(ItemStack stack) { return MAX_CHARGE_TICKS; }

    @Override
    public boolean hasGlint(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        int mode = nbt.getInt("Mode");
        return mode == MODE_VOID || mode == MODE_VORTEX || mode == MODE_PLAGUE || nbt.getInt("ChargeVisual") >= 13;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return stack.getOrCreateNbt().contains("ChargeVisual");
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.max(0, Math.min(13, stack.getOrCreateNbt().getInt("ChargeVisual")));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        int step = stack.getOrCreateNbt().getInt("ChargeVisual");
        if (step >= 13) return 0xDD44FF;
        return MathHelper.hsvToRgb(Math.max(0.0f, step) / 40.0f, 1.0f, 1.0f);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        int mode = stack.getOrCreateNbt().getInt("Mode");
        String key;
        Formatting color;
        switch (mode) {
            case MODE_FIRE:      key = "item.cajadomod.cajado_destruicao.mode.fire";      color = Formatting.RED; break;
            case MODE_LIGHTNING: key = "item.cajadomod.cajado_destruicao.mode.lightning"; color = Formatting.AQUA; break;
            case MODE_VOID:      key = "item.cajadomod.cajado_destruicao.mode.void";      color = Formatting.DARK_PURPLE; break;
            case MODE_TSUNAMI:   key = "item.cajadomod.cajado_destruicao.mode.tsunami";   color = Formatting.BLUE; break;
            case MODE_METEOR:    key = "item.cajadomod.cajado_destruicao.mode.meteor";    color = Formatting.GOLD; break;
            case MODE_PLAGUE:    key = "item.cajadomod.cajado_destruicao.mode.plague";    color = Formatting.DARK_GREEN; break;
            case MODE_VORTEX:    key = "item.cajadomod.cajado_destruicao.mode.vortex";    color = Formatting.LIGHT_PURPLE; break;
            default:             key = "item.cajadomod.cajado_destruicao.mode.fire";      color = Formatting.RED;
        }
        tooltip.add(Text.translatable(key).formatted(color));
        tooltip.add(Text.literal("§8Hold right-click to CHARGE, release to fire").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§8100%% = full power | 200%% = ULTIMATE").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("§8Sneak + right-click: switch mode").formatted(Formatting.GRAY));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) {
            if (!world.isClient) switchMode(world, user, stack);
            return TypedActionResult.success(stack, world.isClient());
        }
        if (!world.isClient) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.4f);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    private void switchMode(World world, PlayerEntity user, ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        int mode = (nbt.getInt("Mode") + 1) % MODE_COUNT;
        nbt.putInt("Mode", mode);
        String name;
        switch (mode) {
            case MODE_FIRE:      name = "§c🔥 Fogo"; break;
            case MODE_LIGHTNING: name = "§b⚡ Raio"; break;
            case MODE_VOID:      name = "§5🌀 Vazio"; break;
            case MODE_TSUNAMI:   name = "§9🌊 Tsunami"; break;
            case MODE_METEOR:    name = "§6☄️ Meteoro"; break;
            case MODE_PLAGUE:    name = "§2💀 Praga"; break;
            case MODE_VORTEX:    name = "§d🌪️ Vórtice"; break;
            default:             name = "?";
        }
        user.sendMessage(Text.literal("§6✦ Modo: " + name + " §6✦"), true);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
            SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.7f, 0.6f + mode * 0.12f);
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(modeParticle(mode), user.getX(), user.getY() + 1.2, user.getZ(), 40, 0.4, 0.6, 0.4, 0.2);
        }
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        int used = getMaxUseTime(stack) - remainingUseTicks;
        float charge = getCharge(used);
        if (world.isClient) {
            stack.getOrCreateNbt().putInt("ChargeVisual",
                Math.max(0, Math.min(13, (int)(charge / 2.0f * 13.0f))));
            return;
        }
        if (!(world instanceof ServerWorld sw)) return;
        int mode = stack.getOrCreateNbt().getInt("Mode");

        if (used % 2 == 0) {
            double radius = Math.max(0.35, 1.6 - charge * 0.8);
            double baseAngle = used * 0.45;
            for (int i = 0; i < 4; i++) {
                double a = baseAngle + i * Math.PI / 2.0;
                double px = user.getX() + Math.cos(a) * radius;
                double pz = user.getZ() + Math.sin(a) * radius;
                double py = user.getY() + 0.3 + (used % 20) * 0.06;
                sw.spawnParticles(modeParticle(mode), px, py, pz, 1, 0.02, 0.02, 0.02, 0.0);
            }
            sw.spawnParticles(ParticleTypes.END_ROD,
                user.getX(), user.getY() + 0.8 + charge * 0.8, user.getZ(), 1, 0.15, 0.15, 0.15, 0.0);
        }
        if (used % 12 == 0) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 0.6f + charge * 0.9f);
        }
        if (user instanceof PlayerEntity p && used % 5 == 0) {
            p.sendMessage(chargeBarText(charge), true);
        }
        if (used == FULL_CHARGE_TICKS) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.6f);
            if (user instanceof PlayerEntity p) {
                p.sendMessage(Text.literal("§a✦ CARGA COMPLETA — segure para OVERCHARGE ✦"), true);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient) {
            stack.getOrCreateNbt().remove("ChargeVisual");
            return;
        }
        if (!(world instanceof ServerWorld sw)) return;
        if (!(user instanceof PlayerEntity player)) return;
        int used = getMaxUseTime(stack) - remainingUseTicks;
        if (used < 3) return;
        float charge = getCharge(used);
        int mode = stack.getOrCreateNbt().getInt("Mode");

        BlockHitResult hit = raycast(world, player, 64.0);
        Vec3d impact = hit.getPos();
        boolean ult = charge >= 2.0f;

        if (ult) {
            player.sendMessage(Text.literal(ultimateName(mode)), false);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        }

        dispatchMode(world, player, impact, charge);
        stack.damage(ult ? 2 : 1, player, e -> {});

        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, ult ? 0.5f : 1.4f - charge * 0.3f);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof PlayerEntity player && !attacker.getWorld().isClient) {
            Vec3d impact = target.getPos().add(0, target.getHeight() / 2.0, 0);
            dispatchMode(attacker.getWorld(), player, impact, 1.0f);
            stack.damage(1, player, e -> {});
        }
        return super.postHit(stack, target, attacker);
    }

    private BlockHitResult raycast(World world, PlayerEntity player, double maxDistance) {
        Vec3d eyePos = player.getEyePos();
        Vec3d endPos = eyePos.add(player.getRotationVec(1.0f).multiply(maxDistance));
        return world.raycast(new RaycastContext(eyePos, endPos,
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
    }

    private Text chargeBarText(float charge) {
        int segments = 10;
        StringBuilder sb = new StringBuilder();
        if (charge < 1.0f) {
            int filled = Math.round(charge * segments);
            sb.append("§6Carga ");
            for (int i = 0; i < segments; i++) sb.append(i < filled ? "§e█" : "§8█");
            sb.append(" §f").append((int)(charge * 100)).append("%");
        } else {
            int over = Math.round((charge - 1.0f) * segments);
            sb.append("§d§lOVERCHARGE ");
            for (int i = 0; i < segments; i++) sb.append(i < over ? "§d█" : "§8█");
            sb.append(" §f").append((int)(charge * 100)).append("%");
        }
        return Text.literal(sb.toString());
    }

    private ParticleEffect modeParticle(int mode) {
        switch (mode) {
            case MODE_FIRE:      return ParticleTypes.FLAME;
            case MODE_LIGHTNING: return ParticleTypes.ELECTRIC_SPARK;
            case MODE_VOID:      return ParticleTypes.REVERSE_PORTAL;
            case MODE_TSUNAMI:   return ParticleTypes.BUBBLE;
            case MODE_METEOR:    return ParticleTypes.LAVA;
            case MODE_PLAGUE:    return ParticleTypes.SOUL;
            case MODE_VORTEX:    return ParticleTypes.PORTAL;
            default:             return ParticleTypes.FLAME;
        }
    }

    private String ultimateName(int mode) {
        switch (mode) {
            case MODE_FIRE:      return "§5✦ §6§lINFERNO NOVA§r §5✦";
            case MODE_LIGHTNING: return "§5✦ §e§lTEMPESTADE DIVINA§r §5✦";
            case MODE_VOID:      return "§5✦ §d§lCOLAPSO DO VAZIO§r §5✦";
            case MODE_TSUNAMI:   return "§5✦ §b§lMAELSTROM§r §5✦";
            case MODE_METEOR:    return "§5✦ §c§lCHUVA DE METEOROS§r §5✦";
            case MODE_PLAGUE:    return "§5✦ §2§lAPOCALIPSE§r §5✦";
            case MODE_VORTEX:    return "§5✦ §9§lSINGULARIDADE§r §5✦";
            default:             return "§5✦ ULTIMATE ✦";
        }
    }

    private void dispatchMode(World world, LivingEntity caster, Vec3d impact, float charge) {
        int mode = caster.getMainHandStack().getOrCreateNbt().getInt("Mode");
        switch (mode) {
            case MODE_FIRE:      castFire(world, caster, impact, charge); break;
            case MODE_LIGHTNING: castLightning(world, caster, impact, charge); break;
            case MODE_VOID:      castVoid(world, caster, impact, charge); break;
            case MODE_TSUNAMI:   castTsunami(world, caster, impact, charge); break;
            case MODE_METEOR:    castMeteor(world, caster, impact, charge); break;
            case MODE_PLAGUE:    castPlague(world, caster, impact, charge); break;
            case MODE_VORTEX:    castVortex(world, caster, impact, charge); break;
        }
    }

    private void drawBeam(ServerWorld sw, Vec3d start, Vec3d end, ParticleEffect p, float charge) {
        Vec3d dir = end.subtract(start);
        double len = dir.length();
        if (len < 0.1) return;
        dir = dir.normalize();
        int steps = (int)(len * 3);
        for (int i = 0; i < steps; i++) {
            Vec3d pos = start.add(dir.multiply(i * 0.33));
            sw.spawnParticles(p, pos.x, pos.y, pos.z, 1 + (int)charge, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private void castFire(World world, LivingEntity caster, Vec3d center, float charge) {
        if (!(world instanceof ServerWorld sw)) return;
        boolean ult = charge >= 2.0f;
        drawBeam(sw, caster.getEyePos(), center, ParticleTypes.FLAME, charge);
        sw.spawnParticles(ParticleTypes.LAVA, center.x, center.y, center.z, (int)(30 * charge), 0.5, 0.8, 0.5, 0.3);
        sw.spawnParticles(ParticleTypes.FLAME, center.x, center.y, center.z, (int)(50 * charge), 0.6, 0.8, 0.6, 0.4);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, (int)(20 * charge), 0.3, 0.5, 0.3, 0.1);
        world.createExplosion(caster, center.x, center.y, center.z, 1.5f + 2.0f * charge, true, World.ExplosionSourceType.TNT);
        Box box = new Box(center, center).expand(3.0 + 2.0 * charge);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e2 -> e2 != caster)) {
            e.setOnFireFor((int)(3 + 4 * charge));
            e.damage(caster.getDamageSources().inFire(), 4.0f + 8.0f * charge);
        }
        if (ult) {
            for (int i = 0; i < 24; i++) {
                double a = (Math.PI * 2 * i) / 24.0;
                sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x + Math.cos(a) * 6, center.y + 0.5, center.z + Math.sin(a) * 6, 8, 0.3, 1.0, 0.3, 0.05);
                sw.spawnParticles(ParticleTypes.LAVA, center.x + Math.cos(a) * 6, center.y + 0.5, center.z + Math.sin(a) * 6, 4, 0.3, 1.0, 0.3, 0.05);
            }
            world.createExplosion(caster, center.x + 3, center.y + 1, center.z + 3, 4.0f, true, World.ExplosionSourceType.TNT);
            world.createExplosion(caster, center.x - 3, center.y + 1, center.z - 3, 4.0f, true, World.ExplosionSourceType.TNT);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.5f, 0.7f);
        }
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, ult ? 0.6f : 1.0f);
    }

    private void castLightning(World world, LivingEntity caster, Vec3d target, float charge) {
        if (!(world instanceof ServerWorld sw)) return;
        boolean ult = charge >= 2.0f;
        Vec3d start = caster.getEyePos();
        drawBeam(sw, start, target, ParticleTypes.ELECTRIC_SPARK, charge);
        drawBeam(sw, start, target, ParticleTypes.ENCHANTED_HIT, charge);
        Box rayBox = new Box(start, target).expand(0.5 + charge);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, rayBox, e2 -> e2 != caster)) {
            e.damage(caster.getDamageSources().lightningBolt(), 5.0f + 8.0f * charge);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
            e.timeUntilRegen = 0;
        }
        sw.spawnParticles(ParticleTypes.FLASH, target.x, target.y + 1, target.z, 1, 0, 0, 0, 0);
        world.playSound(null, target.x, target.y, target.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.0f, 1.2f);
        if (ult) {
            for (int i = 0; i < 5; i++) {
                double a = (Math.PI * 2 * i) / 5.0;
                Vec3d s = target.add(Math.cos(a) * 3.0, 0, Math.sin(a) * 3.0);
                sw.spawnParticles(ParticleTypes.FLASH, s.x, s.y + 1, s.z, 1, 0, 0, 0, 0);
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, s.x, s.y + 1, s.z, 60, 0.5, 2.0, 0.5, 0.3);
                Box b2 = new Box(s, s).expand(2.5);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, b2, e2 -> e2 != caster)) {
                    e.damage(caster.getDamageSources().lightningBolt(), 18.0f);
                    e.timeUntilRegen = 0;
                }
                world.playSound(null, s.x, s.y, s.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 0.8f);
            }
        }
    }

    private void castVoid(World world, LivingEntity caster, Vec3d center, float charge) {
        if (!(world instanceof ServerWorld sw)) return;
        boolean ult = charge >= 2.0f;
        drawBeam(sw, caster.getEyePos(), center, ParticleTypes.REVERSE_PORTAL, charge);
        sw.spawnParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, (int)(100 * charge), 1.5, 1.5, 1.5, 0.5);
        sw.spawnParticles(ParticleTypes.SOUL, center.x, center.y, center.z, (int)(40 * charge), 1.0, 1.0, 1.0, 0.2);
        Box box = new Box(center, center).expand(3.0 + 3.0 * charge);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e2 -> e2 != caster)) {
            e.damage(caster.getDamageSources().magic(), 6.0f + 10.0f * charge);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, (int)(40 + 60 * charge), 1));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 0));
            e.addVelocity(0, 0.3 + 0.3 * charge, 0);
            e.velocityModified = true;
        }
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0f, 0.6f + charge * 0.2f);
        if (ult) {
            sw.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 3, 0.6, 0.6, 0.6, 0.0);
            sw.spawnParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, 120, 2.0, 2.0, 2.0, 0.1);
            Box big = new Box(center, center).expand(10.0);
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, big, e2 -> e2 != caster)) {
                Vec3d pull = center.subtract(e.getPos()).normalize();
                e.addVelocity(pull.x * 1.5, 0.9, pull.z * 1.5);
                e.velocityModified = true;
                e.damage(caster.getDamageSources().outOfWorld(), 30.0f);
                e.timeUntilRegen = 0;
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 120, 3));
            }
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.2f, 0.4f);
        }
    }

    private void castTsunami(World world, LivingEntity caster, Vec3d impact, float charge) {
        if (!(world instanceof ServerWorld sw)) return;
        boolean ult = charge >= 2.0f;
        Vec3d origin = caster.getEyePos();
        Vec3d dir = impact.subtract(origin);
        double dist = dir.length();
        if (dist < 0.1) return;
        dir = dir.normalize();
        int steps = 8 + (int)(6 * charge);
        for (int step = 1; step <= steps; step++) {
            Vec3d w = origin.add(dir.multiply(step * (dist / steps)));
            double r = step * 0.5;
            sw.spawnParticles(ParticleTypes.SPLASH, w.x, w.y, w.z, 30, r, 0.8, r, 0.2);
            sw.spawnParticles(ParticleTypes.BUBBLE, w.x, w.y, w.z, 40, r, 1.0, r, 0.1);
        }
        sw.spawnParticles(ParticleTypes.SPLASH, impact.x, impact.y, impact.z, (int)(80 * charge), 1.5, 1.0, 1.5, 0.3);
        Box box = new Box(impact, impact).expand(4.0 + 2.0 * charge, 3.0, 4.0 + 2.0 * charge);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e2 -> e2 != caster)) {
            Vec3d push = e.getPos().subtract(caster.getPos()).normalize();
            e.addVelocity(push.x * (1.0 + 0.8 * charge), 0.6, push.z * (1.0 + 0.8 * charge));
            e.velocityModified = true;
            e.damage(caster.getDamageSources().drown(), 4.0f + 8.0f * charge);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
        }
        world.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 2.0f, 0.7f);
        if (ult) {
            for (int i = 0; i < 36; i++) {
                double a = (Math.PI * 2 * i) / 36.0;
                for (int r = 2; r <= 10; r += 2) {
                    sw.spawnParticles(ParticleTypes.SPLASH, impact.x + Math.cos(a) * r, impact.y + 0.5, impact.z + Math.sin(a) * r, 2, 0.2, 0.5, 0.2, 0.1);
                    sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, impact.x + Math.cos(a) * r, impact.y, impact.z + Math.sin(a) * r, 2, 0.2, 0.3, 0.2, 0.1);
                }
            }
            Box big = new Box(impact, impact).expand(10.0, 4.0, 10.0);
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, big, e2 -> e2 != caster)) {
                Vec3d push = e.getPos().subtract(impact).normalize();
                e.addVelocity(push.x * 3.0, 1.5, push.z * 3.0);
                e.velocityModified = true;
                e.damage(caster.getDamageSources().drown(), 25.0f);
                e.timeUntilRegen = 0;
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 120, 0));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 200, 2));
            }
            world.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ENTITY_DROWNED_HURT, SoundCategory.PLAYERS, 3.0f, 0.3f);
        }
    }

    private void castMeteor(World world, LivingEntity caster, Vec3d impact, float charge) {
        if (!(world instanceof ServerWorld sw)) return;
        boolean ult = charge >= 2.0f;
        BlockPos targetPos = BlockPos.ofFloored(impact);
        spawnMeteorTrail(sw, targetPos);
        spawnMeteor(sw, targetPos, 3.5f);
        world.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.PLAYERS, 1.2f, 0.5f);
        if (ult) {
            for (int i = 0; i < 5; i++) {
                double a = (Math.PI * 2 * i) / 5.0;
                BlockPos p = targetPos.add((int)Math.round(Math.cos(a) * 3), 0, (int)Math.round(Math.sin(a) * 3));
                spawnMeteorTrail(sw, p);
                spawnMeteor(sw, p, 4.0f);
            }
            world.playSound(null, targetPos.getX(), targetPos.getY() + 20, targetPos.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 3.0f, 0.3f);
        }
    }

    private void spawnMeteor(ServerWorld sw, BlockPos ground, float velocity) {
        BlockPos spawnPos = ground.up(60);
        FallingBlockEntity meteor = FallingBlockEntity.spawnFromBlock(sw, spawnPos, Blocks.MAGMA_BLOCK.getDefaultState());
        if (meteor != null) {
            meteor.setVelocity(0, -velocity, 0);
            meteor.velocityModified = true;
            meteor.setHurtEntities(4.0f, 40);
            meteor.setDestroyedOnLanding();
        }
    }

    private void spawnMeteorTrail(ServerWorld sw, BlockPos ground) {
        for (int i = 0; i < 25; i++) {
            double y = ground.getY() + 60 - i * 2.4;
            sw.spawnParticles(ParticleTypes.LAVA, ground.getX() + 0.5, y, ground.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.05);
            sw.spawnParticles(ParticleTypes.SMOKE, ground.getX() + 0.5, y, ground.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.02);
        }
        sw.spawnParticles(ParticleTypes.FLASH, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.1);
    }

    private void castPlague(World world, LivingEntity caster, Vec3d impact, float charge) {
        if (!(world instanceof ServerWorld sw)) return;
        boolean ult = charge >= 2.0f;
        drawBeam(sw, caster.getEyePos(), impact, ParticleTypes.SOUL, charge);
        BlockPos center = BlockPos.ofFloored(impact);
        int radius = 4 + (int)(4 * charge);
        for (BlockPos pos : BlockPos.iterate(center.add(-radius, -2, -radius), center.add(radius, 3, radius))) {
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            if (block == Blocks.GRASS_BLOCK) {
                world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 3);
            } else if (block == Blocks.GRASS || block == Blocks.TALL_GRASS || block == Blocks.FERN ||
                       block.getTranslationKey().contains("flower") || block.getTranslationKey().contains("sapling") ||
                       block.getTranslationKey().contains("leaves")) {
                world.breakBlock(pos, false, caster);
            }
        }
        sw.spawnParticles(ParticleTypes.SOUL, impact.x, impact.y, impact.z, (int)(80 * charge), 3.0, 1.5, 3.0, 0.2);
        sw.spawnParticles(ParticleTypes.ASH, impact.x, impact.y, impact.z, (int)(60 * charge), 3.0, 2.0, 3.0, 0.2);
        sw.spawnParticles(ParticleTypes.MYCELIUM, impact.x, impact.y - 0.5, impact.z, 60, 3.0, 0.2, 3.0, 0.1);
        Box box = new Box(impact, impact).expand(4.0 + 3.0 * charge);
        int amp = ult ? 2 : 1;
        int dur = ult ? 400 : 200;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e2 -> e2 != caster)) {
            e.damage(caster.getDamageSources().wither(), 4.0f + 6.0f * charge);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, dur, amp));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, dur, amp));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, dur, amp));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, dur, amp));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
        }
        world.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f + charge * 0.3f, 0.6f);
        if (ult) {
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, impact.x, impact.y, impact.z, 120, 5.0, 2.0, 5.0, 0.1);
            world.playSound(null, impact.x, impact.y, impact.z, SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 3.0f, 0.2f);
        }
    }

    private void castVortex(World world, LivingEntity caster, Vec3d center, float charge) {
        if (!(world instanceof ServerWorld sw)) return;
        boolean ult = charge >= 2.0f;
        drawBeam(sw, caster.getEyePos(), center, ParticleTypes.PORTAL, charge);
        for (int ring = 0; ring < 5; ring++) {
            double r = (1.0 + ring) * (0.8 + charge * 0.4);
            int count = 15 + ring * 4;
            for (int i = 0; i < count; i++) {
                double a = (Math.PI * 2 * i) / (double) count + ring * 0.5;
                sw.spawnParticles(ParticleTypes.PORTAL, center.x + Math.cos(a) * r, center.y + ring * 0.3 - 0.5, center.z + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.05);
            }
        }
        sw.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, center.x, center.y, center.z, (int)(40 * charge), 0.8, 0.8, 0.8, 0.1);
        Box box = new Box(center, center).expand(5.0 + 4.0 * charge);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e2 -> e2 != caster)) {
            Vec3d pull = center.subtract(e.getPos()).normalize();
            double d = e.getPos().distanceTo(center);
            double s = Math.max(0.3, (1.0 + charge) - d * 0.08);
            e.addVelocity(pull.x * s, pull.y * s * 0.5 + 0.2, pull.z * s);
            e.velocityModified = true;
            e.damage(caster.getDamageSources().magic(), 8.0f + 10.0f * charge);
            e.timeUntilRegen = 0;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, ult ? 4 : 2));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0));
        }
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.5f, 0.3f);
        if (ult) {
            world.createExplosion(caster, center.x, center.y, center.z, 4.0f, false, World.ExplosionSourceType.TNT);
            sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 2, 0.5, 0.5, 0.5, 0);
            sw.spawnParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y, center.z, 150, 1.0, 1.0, 1.0, 0.1);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.PLAYERS, 2.0f, 0.2f);
        } else {
            world.createExplosion(caster, center.x, center.y, center.z, 1.0f, false, World.ExplosionSourceType.NONE);
        }
    }
}
