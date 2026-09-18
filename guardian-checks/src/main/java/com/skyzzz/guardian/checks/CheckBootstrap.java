package com.skyzzz.guardian.checks;

import com.skyzzz.guardian.api.check.Check;
import com.skyzzz.guardian.checks.combat.AimCheck;
import com.skyzzz.guardian.checks.combat.AutoclickerCheck;
import com.skyzzz.guardian.checks.combat.FakeCriticalsCheck;
import com.skyzzz.guardian.checks.combat.KillauraCheck;
import com.skyzzz.guardian.checks.combat.ReachCheck;
import com.skyzzz.guardian.checks.combat.VelocityCheck;
import com.skyzzz.guardian.checks.movement.ElytraCheck;
import com.skyzzz.guardian.checks.movement.FlyCheck;
import com.skyzzz.guardian.checks.movement.JesusCheck;
import com.skyzzz.guardian.checks.movement.NoFallCheck;
import com.skyzzz.guardian.checks.movement.PhaseCheck;
import com.skyzzz.guardian.checks.movement.SneakCheck;
import com.skyzzz.guardian.checks.movement.SpeedCheck;
import com.skyzzz.guardian.checks.movement.StepCheck;
import com.skyzzz.guardian.checks.movement.TimerCheck;
import com.skyzzz.guardian.checks.movement.VehicleCheck;
import com.skyzzz.guardian.checks.packet.ClientBrandCheck;
import com.skyzzz.guardian.checks.packet.FloodCheck;
import com.skyzzz.guardian.checks.packet.PacketSanityCheck;
import com.skyzzz.guardian.checks.packet.PingSpoofCheck;
import com.skyzzz.guardian.checks.player.AutoRespawnCheck;
import com.skyzzz.guardian.checks.player.FastUseCheck;
import com.skyzzz.guardian.checks.player.InventoryCheck;
import com.skyzzz.guardian.checks.player.RegenCheck;
import com.skyzzz.guardian.checks.world.AutoToolCheck;
import com.skyzzz.guardian.checks.world.BlockReachCheck;
import com.skyzzz.guardian.checks.world.FastBreakCheck;
import com.skyzzz.guardian.checks.world.FastPlaceCheck;
import com.skyzzz.guardian.checks.world.NukerCheck;
import com.skyzzz.guardian.checks.world.ScaffoldCheck;
import com.skyzzz.guardian.checks.world.XrayHeuristicCheck;

import java.util.List;

/**
 * Single place that knows the full check list. Core calls {@link #create()} once at
 * startup; nothing else in the codebase references concrete check classes.
 */
public final class CheckBootstrap {

    private CheckBootstrap() {
    }

    public static List<Check> create() {
        return List.of(
                // Combat
                new KillauraCheck(),
                new ReachCheck(),
                new AutoclickerCheck(),
                new AimCheck(),
                new VelocityCheck(),
                new FakeCriticalsCheck(),

                // Movement
                new SpeedCheck(),
                new FlyCheck(),
                new NoFallCheck(),
                new JesusCheck(),
                new PhaseCheck(),
                new StepCheck(),
                new SneakCheck(),
                new TimerCheck(),
                new ElytraCheck(),
                new VehicleCheck(),

                // World
                new ScaffoldCheck(),
                new FastPlaceCheck(),
                new FastBreakCheck(),
                new NukerCheck(),
                new AutoToolCheck(),
                new BlockReachCheck(),
                new XrayHeuristicCheck(),

                // Player
                new FastUseCheck(),
                new AutoRespawnCheck(),
                new RegenCheck(),
                new InventoryCheck(),

                // Packet
                new PacketSanityCheck(),
                new FloodCheck(),
                new ClientBrandCheck(),
                new PingSpoofCheck()
        );
    }
}