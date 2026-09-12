package com.example.cajadomod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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

    public DestructionStaffItem(Settings settings) { super(settings); }

    @Override
    public boolean hasGlint(ItemStack stack) {
        int mode = stack.getOrCreateNbt().getInt("Mode");
        return mode == MODE_VOID || mode == MODE_VORTEX || mode == MODE_PLAGUE;
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
        tooltip.add(Text.literal("§8Right-click: shoot | Sneak+Right: switch").formatted(Formatting.GRAY));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            if (user.isSneaking()) {
                NbtCompound nbt = stack.getOrCreateNbt();
                int mode = nbt.getInt("Mode");
                mode = (mode + 1) % MODE_COUNT;
                nbt.putInt("Mode", mode);
                String modeName;
                switch (mode) {
                    case MODE_FIRE:      modeName = "§c🔥 Fogo"; break;
                    case MODE_LIGHTNING: modeName = "§b⚡ Raio"; break;
                    case MODE_VOID:      modeName = "§5🌀 Vazio"; break;
                    case MODE_TSUNAMI:   modeName = "§9🌊 Tsunami"; break;
                    case MODE_METEOR:    modeName = "§6☄️ Meteoro"; break;
                    case MODE_PLAGUE:    modeName = "§2💀 Praga"; break;
                    case MODE_VORTEX:    modeName = "§d🌪️ Vórtice"; break;
                    default:             modeName = "Desconhecido";
                }
                user.sendMessage(Text.literal("§6✦ Modo: " + modeName + " §6✦"), true);
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.7f, 0.8f);
                return TypedActionResult.success(stack, true);
            }

            BlockHitResult hit = raycast(world, user, 64.0);
            Vec3d impact = hit.getPos();
            dispatchMode(world, user, impact, null);
            stack.damage(1, user, e -> {});
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof PlayerEntity player && !attacker.getWorld().isClient) {
            Vec3d impact = target.getPos().add(0, target.getHeight()/2.0, 0);
            dispatchMode(attacker.getWorld(), player, impact, target);
            stack.damage(1, attacker, e -> {});
        }
        return super.postHit(stack, target, attacker);
    }

    private BlockHitResult raycast(World world, PlayerEntity player, double maxDistance) {
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f).multiply(maxDistance);
        Vec3d endPos = eyePos.add(lookVec);
        return world.raycast(new RaycastContext(eyePos, endPos,
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
    }

    private void dispatchMode(World world, PlayerEntity caster, Vec3d impact, Entity target) {
        int mode = caster.getMainHandStack().getOrCreateNbt().getInt("Mode");
        switch (mode) {
            case MODE_FIRE:      castFire(world, caster, impact, target); break;
            case MODE_LIGHTNING: castLightning(world, caster, impact, target); break;
            case MODE_VOID:      castVoid(world, caster, impact, target); break;
            case MODE_TSUNAMI:   castTsunami(world, caster, impact, target); break;
            case MODE_METEOR:    castMeteor(world, caster, impact, target); break;
            case MODE_PLAGUE:    castPlague(world, caster, impact, target); break;
            case MODE_VORTEX:    castVortex(world, caster, impact, target); break;
        }
    }

    private void drawBeam(ServerWorld sw, Vec3d start, Vec3d end, net.minecraft.particle.ParticleEffect p) {
        Vec3d dir = end.subtract(start);
        double len = dir.length();
        dir = dir.normalize();
        int steps = (int)(len * 3);
        for (int i = 0; i < steps; i++) {
            Vec3d p_ = start.add(dir.multiply(i * 0.33));
            sw.spawnParticles(p, p_.x, p_.y, p_.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private void castFire(World world, LivingEntity caster, Vec3d center, Entity target) {
        if (!(world instanceof ServerWorld sw)) return;
        drawBeam(sw, caster.getEyePos(), center, ParticleTypes.FLAME);
        sw.spawnParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 50, 0.5, 0.8, 0.5, 0.3);
        sw.spawnParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 80, 0.6, 0.8, 0.6, 0.4);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 30, 0.3, 0.5, 0.3, 0.1);
        world.createExplosion(caster, center.x, center.y, center.z, 3.0f, true, World.ExplosionSourceType.TNT);
        Box box = new Box(center, center).expand(4.0);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e_ -> e_ != caster)) {
            e.setOnFireFor(6); e.damage(caster.getDamageSources().inFire(), 6.0f);
        }
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 0.9f);
    }

    private void castLightning(World world, LivingEntity caster, Vec3d target, Entity entity) {
        if (!(world instanceof ServerWorld sw)) return;
        Vec3d start = caster.getEyePos();
        drawBeam(sw, start, target, ParticleTypes.ELECTRIC_SPARK);
        drawBeam(sw, start, target, ParticleTypes.ENCHANTED_HIT);
        Box rayBox = new Box(start, target).expand(1.0);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, rayBox, e_ -> e_ != caster)) {
            e.damage(caster.getDamageSources().lightningBolt(), 10.0f);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
            e.timeUntilRegen = 0;
        }
        sw.spawnParticles(ParticleTypes.FLASH, target.x, target.y, target.z, 1, 0, 0, 0, 0);
        world.playSound(null, target.x, target.y, target.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    private void castVoid(World world, LivingEntity caster, Vec3d center, Entity target) {
        if (!(world instanceof ServerWorld sw)) return;
        drawBeam(sw, caster.getEyePos(), center, ParticleTypes.PORTAL);
        sw.spawnParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 200, 1.5, 1.5, 1.5, 0.5);
        sw.spawnParticles(ParticleTypes.SOUL, center.x, center.y, center.z, 80, 1.0, 1.0, 1.0, 0.2);
        sw.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        Box box = new Box(center, center).expand(5.0);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e_ -> e_ != caster)) {
            e.damage(caster.getDamageSources().magic(), 14.0f);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 80, 2));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
            e.addVelocity(0, 0.6, 0); e.velocityModified = true;
        }
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2f, 0.6f);
    }

    private void castTsunami(World world, LivingEntity caster, Vec3d impact, Entity target) {
        if (!(world instanceof ServerWorld sw)) return;
        Vec3d origin = caster.getEyePos();
        Vec3d dir = impact.subtract(origin).normalize();
        for (int step = 1; step <= 12; step++) {
            Vec3d waveCenter = origin.add(dir.multiply(step * 1.2));
            double r = step * 0.6;
            sw.spawnParticles(ParticleTypes.SPLASH, waveCenter.x, waveCenter.y, waveCenter.z, 40, r, 0.8, r, 0.2);
            sw.spawnParticles(ParticleTypes.BUBBLE, waveCenter.x, waveCenter.y, waveCenter.z, 60, r, 1.0, r, 0.1);
        }
        sw.spawnParticles(ParticleTypes.SPLASH, impact.x, impact.y, impact.z, 150, 1.5, 1.0, 1.5, 0.3);
        Box box = new Box(impact, impact).expand(6.0, 3.0, 6.0);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e_ -> e_ != caster)) {
            Vec3d push = e.getPos().subtract(caster.getPos()).normalize();
            e.addVelocity(push.x * 1.8, 0.8, push.z * 1.8); e.velocityModified = true;
            e.damage(caster.getDamageSources().drown(), 12.0f);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 3));
        }
        world.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 2.5f, 0.7f);
    }

    private void castMeteor(World world, LivingEntity caster, Vec3d impact, Entity target) {
        if (!(world instanceof ServerWorld sw)) return;
        BlockPos targetPos = new BlockPos((int)Math.floor(impact.x),(int)Math.floor(impact.y),(int)Math.floor(impact.z));
        BlockPos spawnPos = targetPos.up(60);
        for (int i = 0; i < 40; i++) {
            double y = spawnPos.getY() - i * 1.5;
            sw.spawnParticles(ParticleTypes.LAVA, targetPos.getX()+0.5, y, targetPos.getZ()+0.5, 3, 0.1, 0.1, 0.1, 0.05);
        }
        FallingBlockEntity meteor = FallingBlockEntity.spawnFromBlock(sw, spawnPos, Blocks.MAGMA_BLOCK.getDefaultState());
        if (meteor != null) {
            meteor.setVelocity(0, -3.5, 0); meteor.velocityModified = true;
            meteor.setHurtEntities(4.0f, 40);
            meteor.setDestroyedOnLanding();
        }
        world.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.PLAYERS, 1.5f, 0.4f);
    }

    private void castPlague(World world, LivingEntity caster, Vec3d impact, Entity target) {
        if (!(world instanceof ServerWorld sw)) return;
        drawBeam(sw, caster.getEyePos(), impact, ParticleTypes.SOUL);
        BlockPos center = new BlockPos((int)Math.floor(impact.x),(int)Math.floor(impact.y),(int)Math.floor(impact.z));
        int broken = 0;
        for (BlockPos pos : BlockPos.iterate(center.add(-6,-2,-6), center.add(6,3,6))) {
            BlockState state = world.getBlockState(pos); Block block = state.getBlock();
            if (block == Blocks.GRASS_BLOCK) { world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 3); broken++; }
            else if (block == Blocks.GRASS || block == Blocks.TALL_GRASS || block == Blocks.FERN ||
                     block.getTranslationKey().contains("flower") || block.getTranslationKey().contains("sapling") ||
                     block.getTranslationKey().contains("leaves")) { world.breakBlock(pos, false, caster); broken++; }
        }
        sw.spawnParticles(ParticleTypes.SOUL, impact.x, impact.y, impact.z, 150, 3.0, 1.5, 3.0, 0.2);
        sw.spawnParticles(ParticleTypes.ASH, impact.x, impact.y, impact.z, 100, 3.0, 2.0, 3.0, 0.2);
        sw.spawnParticles(ParticleTypes.MYCELIUM, impact.x, impact.y-0.5, impact.z, 80, 3.0, 0.2, 3.0, 0.1);
        Box box = new Box(impact, impact).expand(7.0);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e_ -> e_ != caster)) {
            e.damage(caster.getDamageSources().wither(), 8.0f);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 200, 2));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200, 1));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
        }
        world.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.5f, 0.6f);
    }

    private void castVortex(World world, LivingEntity caster, Vec3d center, Entity target) {
        if (!(world instanceof ServerWorld sw)) return;
        drawBeam(sw, caster.getEyePos(), center, ParticleTypes.PORTAL);
        for (int ring = 0; ring < 5; ring++) {
            double r = 1.5 + ring;
            for (int i = 0; i < 20 + ring*5; i++) {
                double a = (Math.PI*2*i)/(20+ring*5) + ring*0.5;
                sw.spawnParticles(ParticleTypes.PORTAL, center.x+Math.cos(a)*r, center.y+ring*0.3-0.5, center.z+Math.sin(a)*r, 3, 0.1, 0.1, 0.1, 0.05);
            }
        }
        sw.spawnParticles(ParticleTypes.SOUL, center.x, center.y, center.z, 100, 0.5, 0.5, 0.5, 0.15);
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        Box box = new Box(center, center).expand(8.0);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, e_ -> e_ != caster)) {
            Vec3d pull = center.subtract(e.getPos()).normalize();
            double d = e.getPos().distanceTo(center);
            double s = Math.max(0.3, 1.5 - d*0.1);
            e.addVelocity(pull.x*s, pull.y*s*0.5+0.2, pull.z*s); e.velocityModified = true;
            e.damage(caster.getDamageSources().magic(), 16.0f); e.timeUntilRegen = 0;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 4));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0));
        }
        world.createExplosion(caster, center.x, center.y, center.z, 1.5f, false, World.ExplosionSourceType.NONE);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 2.0f, 0.2f);
    }
}
