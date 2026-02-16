package net.david.mod.block;

import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.registry.ModBlocks;
import net.david.mod.util.ProtectionDataManager;
import net.david.mod.util.ClanSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ProtectionCoreBlock extends Block implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 5);

    public ProtectionCoreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH)
                .setValue(LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, LEVEL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Forma de bloque completo para ambas partes
        return Shapes.block();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Solo la parte inferior contiene la BlockEntity con los datos
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new ProtectionCoreBlockEntity(pos, state) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof Player player)) return;
        ServerLevel sLevel = (ServerLevel) level;

        // 1. Validar espacio superior
        if (!level.getBlockState(pos.above()).isAir()) {
            cancelarColocacion(level, pos, player, stack, "§c[!] No hay espacio suficiente arriba.");
            return;
        }

        boolean isAdminCore = state.is(ModBlocks.ADMIN_PROTECTOR);
        ProtectionDataManager manager = ProtectionDataManager.get(sLevel);

        // 2. Validar límites y solapamientos (Solo para jugadores normales)
        if (!isAdminCore && !player.hasPermissions(2)) {
            // Límite de núcleos (hardcoded a 3 por ahora)
            long coreCount = manager.getAllCores().values().stream()
                    .filter(entry -> player.getUUID().equals(entry.owner()))
                    .count();

            if (coreCount >= 3) {
                cancelarColocacion(level, pos, player, stack, "§c[!] Has alcanzado el límite de 3 núcleos.");
                return;
            }

            // Validar distancia mínima (Radio inicial 10 + margen)
            boolean tooClose = manager.getAllCores().entrySet().stream()
                    .anyMatch(e -> Math.sqrt(pos.distSqr(e.getKey())) < (10 + e.getValue().radius()));

            if (tooClose) {
                cancelarColocacion(level, pos, player, stack, "§c[!] Estás demasiado cerca de otra zona.");
                return;
            }
        }

        // 3. Colocación exitosa: Crear parte superior y asignar dueño
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
            core.setOwner(player);
            manager.addCore(pos, player.getUUID(), core.getRadius(), isAdminCore, false);
            manager.syncToAll(sLevel);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level instanceof ServerLevel sLevel) {
                // Si rompemos la base (LOWER), limpiamos todo el sistema
                if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof ProtectionCoreBlockEntity core) {
                        ProtectionDataManager.get(sLevel).removeCore(pos);
                        ProtectionDataManager.get(sLevel).syncToAll(sLevel);

                        // Eliminar clan asociado si el dueño rompe su core
                        if (core.getOwnerUUID() != null) {
                            ClanSavedData.get(sLevel.getServer()).deleteClan(core.getOwnerUUID());
                        }
                    }
                }

                // Limpieza de la otra mitad del bloque
                BlockPos otherPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
                if (level.getBlockState(otherPos).is(this)) {
                    level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        // Redirigir siempre la interacción a la base (donde está la BE)
        BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (level.getBlockEntity(basePos) instanceof ProtectionCoreBlockEntity core) {

            if (core.isAdmin()) {
                if (player.hasPermissions(2)) {
                    player.openMenu(core);
                    return InteractionResult.SUCCESS;
                }
                player.displayClientMessage(Component.literal("§c[!] Solo administradores."), true);
                return InteractionResult.CONSUME;
            }

            if (core.isTrusted(player)) {
                player.openMenu(core);
                return InteractionResult.SUCCESS;
            }
        }

        player.displayClientMessage(Component.literal("§c[!] No tienes acceso a este núcleo."), true);
        return InteractionResult.CONSUME;
    }

    private void cancelarColocacion(Level level, BlockPos pos, Player player, ItemStack stack, String mensaje) {
        player.displayClientMessage(Component.literal(mensaje), true);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        // Devolver item si no es creativo
        if (!player.getAbilities().instabuild) {
            player.getInventory().add(stack.copy());
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        // Si se rompe una mitad, la otra se convierte en aire
        if (direction.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (direction == Direction.UP)) {
            return neighborState.is(this) && neighborState.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
}