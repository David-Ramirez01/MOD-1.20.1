package net.david.mod.block;

import net.david.mod.blockentity.AdminProtectorBlockEntity;
import net.david.mod.blockentity.ProtectionCoreBlockEntity;
import net.david.mod.util.ProtectionDataManager;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AdminProtectorBlock extends Block implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public AdminProtectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        // Verificar límite de altura y que el bloque de arriba sea reemplazable
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
        }
        return Shapes.or(
                Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D), // Base
                Block.box(2.0D, 4.0D, 2.0D, 14.0D, 16.0D, 14.0D) // Pilar
        );
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // IMPORTANTE: Retornar la versión administrativa de la BlockEntity
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new AdminProtectorBlockEntity(pos, state) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof Player player)) return;

        // Seguridad extrema: Si no es OP, el bloque se auto-destruye sin soltar nada
        if (!player.hasPermissions(2)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            player.displayClientMessage(Component.literal("§c[!] Solo el personal autorizado puede usar tecnología administrativa."), true);
            return;
        }

        // Colocar la mitad superior
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);

        if (level.getBlockEntity(pos) instanceof AdminProtectorBlockEntity adminBE) {
            adminBE.setOwner(player);
            // Registrar como Admin Core en el Manager
            if (level instanceof ServerLevel sLevel) {
                ProtectionDataManager.get(sLevel).addCore(pos, player.getUUID(), adminBE.getRadius(), true, false);
                ProtectionDataManager.get(sLevel).syncToAll(sLevel);
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (level.getBlockEntity(basePos) instanceof ProtectionCoreBlockEntity core) {
            // Solo personal con permiso nivel 2 puede abrir la interfaz de administración
            if (player.hasPermissions(2)) {
                player.openMenu(core);
                return InteractionResult.SUCCESS;
            } else {
                player.displayClientMessage(Component.literal("§c[!] Consola administrativa bloqueada."), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level instanceof ServerLevel sLevel) {
                // Si se elimina la base, limpiar el manager
                if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    ProtectionDataManager.get(sLevel).removeCore(pos);
                    ProtectionDataManager.get(sLevel).syncToAll(sLevel);
                }

                // Limpiar la otra mitad
                BlockPos otherPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
                if (level.getBlockState(otherPos).is(this)) {
                    level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            BlockEntity be = level.getBlockEntity(basePos);

            // Impedir que jugadores normales rompan el bloque de administración incluso en survival
            if (be instanceof AdminProtectorBlockEntity && !player.hasPermissions(2)) {
                player.displayClientMessage(Component.literal("§c[!] Este bloque está anclado a la realidad por el staff."), true);
                // Cancelar visualmente la rotura enviando el estado actual de nuevo
                level.sendBlockUpdated(pos, state, state, 3);
                return;
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}