package org.bxwbb.qcpet.utils.saveUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 宠物持久化存储抽象接口。
 * 有 MySQL 实现（MySqlSaveUtil）和本地文件实现（LocalFileSaveUtil）。
 */
public interface PetStorage extends AutoCloseable {

    /**
     * 分配下一个宠物自增 ID
     */
    long nextPetId();

    /**
     * 按 ID 查询单条宠物
     */
    Optional<MySqlSaveUtil.PetRecord> findPet(long petId);

    /**
     * 查询玩家名下全部宠物
     */
    List<MySqlSaveUtil.PetRecord> findPetsByPlayer(UUID playerUuid);

    /**
     * 保存/更新宠物
     */
    void savePet(MySqlSaveUtil.PetRecord pet);

    /**
     * 绑定宠物归属
     */
    void bindPetToPlayer(UUID playerUuid, long petId);

    /**
     * 删除玩家名下指定宠物
     */
    boolean deletePet(UUID playerUuid, long petId);

    /**
     * 存储是否可用
     */
    boolean isAvailable();

    @Override
    void close();
}
