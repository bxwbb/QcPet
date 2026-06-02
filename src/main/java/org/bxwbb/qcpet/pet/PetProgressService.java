package org.bxwbb.qcpet.pet;

import org.bxwbb.qcpet.math.MathExpression;

public class PetProgressService {

    private static final int MIN_REQUIRED_EXP = 1;

    private final PetConfigManger petConfigManger;

    public PetProgressService(PetConfigManger petConfigManger) {
        this.petConfigManger = petConfigManger;
    }

    public int getRequiredExp(Pet pet) {
        PetConfig config = petConfigManger.pets.get(pet.type());
        if (config == null) {
            return 100;
        }

        MathExpression expression = new MathExpression();
        expression.parse(config.levelExpRequirement());
        double value = expression.calculateDouble(pet.level() + 1, pet.level(), pet.exp());
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 100;
        }
        return Math.max(MIN_REQUIRED_EXP, (int) Math.ceil(value));
    }

    public Pet addExperience(Pet pet, int amount) {
        if (pet == null) {
            throw new IllegalArgumentException("pet cannot be null");
        }
        if (amount <= 0) {
            return pet;
        }

        int level = pet.level();
        int exp = pet.exp() + amount;
        Pet current = pet;
        while (true) {
            current = new Pet(
                    pet.id(),
                    pet.name(),
                    pet.type(),
                    level,
                    exp,
                    pet.times(),
                    pet.data(),
                    pet.show(),
                    pet.owner(),
                    pet.entity()
            );
            int requiredExp = getRequiredExp(current);
            if (exp < requiredExp) {
                return current;
            }
            exp -= requiredExp;
            level++;
        }
    }

    public Pet addLevels(Pet pet, int amount) {
        if (pet == null) {
            throw new IllegalArgumentException("pet cannot be null");
        }
        if (amount <= 0) {
            return pet;
        }
        return new Pet(
                pet.id(),
                pet.name(),
                pet.type(),
                pet.level() + amount,
                pet.exp(),
                pet.times(),
                pet.data(),
                pet.show(),
                pet.owner(),
                pet.entity()
        );
    }

    public double getProgress(Pet pet) {
        int requiredExp = getRequiredExp(pet);
        return Math.max(0D, Math.min(1D, pet.exp() / (double) requiredExp));
    }

    public String buildProgressBar(Pet pet, int length) {
        int safeLength = Math.max(1, length);
        int filled = (int) Math.round(getProgress(pet) * safeLength);
        StringBuilder builder = new StringBuilder(safeLength);
        for (int index = 0; index < safeLength; index++) {
            builder.append(index < filled ? '#' : '-');
        }
        return builder.toString();
    }
}
