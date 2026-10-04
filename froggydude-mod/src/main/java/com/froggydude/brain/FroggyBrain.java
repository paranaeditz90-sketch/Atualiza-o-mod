package com.froggydude.brain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O cérebro que aprende: pequeno, offline, dentro do mod, e salvo no mundo.
 *
 * Duas decisões aprendidas por jogador:
 * 1. COMO começar a caçada ({@link Strategy}): um bandido contextual - pra cada
 *    estilo do jogador (foge, torre, briga, atira, esconde) ele guarda quanto cada
 *    estratégia rendeu (Q = média das recompensas) e escolhe pela maior Q mais um
 *    bônus de curiosidade (UCB) pro que ainda testou pouco.
 * 2. QUAL ataque usar: amostragem de Thompson - cada ataque tem acertos/erros
 *    contra ESSE jogador; sorteia uma taxa de acerto plausível pra cada um e pesa
 *    a escolha por ela. Quem desvia sempre da língua vê menos língua.
 *
 * A recompensa de uma caçada: dano causado, derrubadas, matar (muito), menos o
 * dano que tomou, a vítima escapar, ele morrer e o tempo gasto (manhunt é corrida).
 */
public class FroggyBrain {

    /** Bônus de curiosidade (quanto ele arrisca uma estratégia pouco testada). */
    private static final float UCB = 2.5F;
    /**
     * Quanto ele "acha" que vale uma estratégia que nunca testou (otimista: senão,
     * uma vitória fácil logo na primeira caçada travava ele nela pra sempre).
     */
    private static final float OPTIMISM = 3.0F;
    /** Taxa mínima de aprendizado (o jogador muda de tática; ele acompanha). */
    private static final float MIN_ALPHA = 0.1F;
    /** Esquecimento dos acertos/erros a cada ataque novo. */
    private static final float ATTACK_DECAY = 0.97F;

    private final Map<UUID, PlayerMemory> players = new HashMap<>();

    public PlayerMemory memory(UUID id, String name) {
        PlayerMemory m = players.computeIfAbsent(id, k -> new PlayerMemory());
        if (name != null && !name.isEmpty()) m.name = name;
        return m;
    }

    public PlayerMemory find(UUID id) {
        return players.get(id);
    }

    public Map<UUID, PlayerMemory> all() {
        return players;
    }

    public void forget(UUID id) {
        players.remove(id);
    }

    // ------------------------------------------------------------ estratégia

    /**
     * Escolhe a estratégia pra caçar esse jogador. {@code bias} soma uma
     * preferência fixa por estratégia (a pressão do manhunt), pode ser null.
     */
    public Strategy choose(PlayerMemory m, float[] bias, RandomSource random) {
        Style ctx = m.dominantStyle();
        int c = ctx.ordinal();
        int total = 0;
        for (int a = 0; a < PlayerMemory.STRATEGIES; a++) total += m.n[c][a];
        Strategy best = Strategy.RUSH;
        float bestScore = -Float.MAX_VALUE;
        for (Strategy s : Strategy.values()) {
            int a = s.ordinal();
            // nunca testou contra esse estilo: usa o que já sabe desse jogador nos
            // outros estilos; se nunca testou de jeito nenhum, é otimista
            float est = m.n[c][a] > 0 ? m.q[c][a] : overall(m, a);
            float score = est
                    + UCB * (float) Math.sqrt(Math.log(total + 2.0D) / (m.n[c][a] + 1.0D))
                    + (bias == null ? 0F : bias[a])
                    + (float) random.nextGaussian() * 0.15F; // desempate e um pouco de imprevisível
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /** Valor médio da estratégia contra esse jogador em todos os estilos (OPTIMISM se nunca testou). */
    private static float overall(PlayerMemory m, int a) {
        float sum = 0F;
        int n = 0;
        for (int c = 0; c < PlayerMemory.STYLES; c++) {
            sum += m.q[c][a] * m.n[c][a];
            n += m.n[c][a];
        }
        return n == 0 ? OPTIMISM : sum / n;
    }

    /** Aprende com o resultado de uma caçada. */
    public void learn(PlayerMemory m, Style ctx, Strategy s, float reward) {
        int c = ctx.ordinal(), a = s.ordinal();
        m.n[c][a]++;
        float alpha = Math.max(1.0F / m.n[c][a], MIN_ALPHA);
        m.q[c][a] += alpha * (reward - m.q[c][a]);
    }

    // ------------------------------------------------------------ ataques

    /** Uma taxa de acerto plausível pra esse ataque contra esse jogador (Thompson). */
    public float sampleHitRate(PlayerMemory m, String attack, RandomSource random) {
        float[] hm = m.attacks.get(attack);
        float hit = hm == null ? 0F : hm[0];
        float miss = hm == null ? 0F : hm[1];
        return beta(hit + 1.0D, miss + 1.0D, random);
    }

    public void recordAttack(PlayerMemory m, String attack, boolean hit) {
        float[] hm = m.attack(attack);
        hm[0] *= ATTACK_DECAY;
        hm[1] *= ATTACK_DECAY;
        hm[hit ? 0 : 1] += 1.0F;
    }

    /** Taxa média de acerto observada (pra mostrar no /froggydude cerebro). */
    public static float meanHitRate(PlayerMemory m, String attack) {
        float[] hm = m.attacks.get(attack);
        if (hm == null) return 0.5F;
        return (hm[0] + 1.0F) / (hm[0] + hm[1] + 2.0F);
    }

    // ------------------------------------------------------------ recompensa

    public enum Outcome { KILLED, ESCAPED, FROGGY_DIED, ONGOING_SWITCH }

    /**
     * Quanto rendeu uma caçada. Escala: matar vale ~6, a vítima fugir -3, ele
     * morrer -4; dano causado/tomado em corações de vida.
     */
    public static float reward(float dealt, float taken, int pins, Outcome outcome, int ticks) {
        float r = dealt / 10.0F + 2.0F * pins - taken / 25.0F - 0.5F * (ticks / 1200.0F);
        switch (outcome) {
            case KILLED -> r += 6.0F;
            case ESCAPED -> r -= 3.0F;
            case FROGGY_DIED -> r -= 4.0F;
            default -> {
            }
        }
        return Math.max(-10.0F, Math.min(12.0F, r));
    }

    // ------------------------------------------------------------ amostragem

    /** Beta(a, b) = X / (X + Y), X ~ Gama(a), Y ~ Gama(b). */
    static float beta(double a, double b, RandomSource r) {
        double x = gamma(a, r), y = gamma(b, r);
        return (float) (x / (x + y));
    }

    /** Gama(forma >= 1) pelo método de Marsaglia e Tsang. */
    static double gamma(double shape, RandomSource r) {
        double d = shape - 1.0D / 3.0D, c = 1.0D / Math.sqrt(9.0D * d);
        while (true) {
            double x, v;
            do {
                x = r.nextGaussian();
                v = 1.0D + c * x;
            } while (v <= 0.0D);
            v = v * v * v;
            double u = r.nextDouble();
            if (u < 1.0D - 0.0331D * x * x * x * x) return d * v;
            if (Math.log(u) < 0.5D * x * x + d * (1.0D - v + Math.log(v))) return d * v;
        }
    }

    // ------------------------------------------------------------ salvar

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        players.forEach((id, m) -> tag.put(id.toString(), m.save()));
        return tag;
    }

    public void load(CompoundTag tag) {
        players.clear();
        for (String k : tag.getAllKeys()) {
            try {
                players.put(UUID.fromString(k), PlayerMemory.load(tag.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
                // chave estragada: ignora esse jogador
            }
        }
    }
}
