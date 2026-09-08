package com.freepark.local.configsync;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.freepark.driver.api.model.VehicleType;
import com.freepark.local.domain.AccessJudgmentRuleType;
import com.freepark.local.domain.BlacklistVehicle;
import com.freepark.local.domain.BlacklistVehicleRepository;
import com.freepark.local.domain.InterceptRuleType;
import com.freepark.local.domain.InternalVehicle;
import com.freepark.local.domain.InternalVehicleRepository;
import com.freepark.local.domain.LaneType;
import com.freepark.local.domain.LotType;
import com.freepark.local.domain.ParkingArea;
import com.freepark.local.domain.ParkingAreaRepository;
import com.freepark.local.domain.ParkingLane;
import com.freepark.local.domain.ParkingLaneRepository;
import com.freepark.local.domain.ParkingLocation;
import com.freepark.local.domain.ParkingLocationRepository;
import com.freepark.local.domain.ParkingLot;
import com.freepark.local.domain.ParkingLotRepository;
import com.freepark.local.domain.ParkingSpace;
import com.freepark.local.domain.ParkingSpaceRepository;
import com.freepark.local.domain.PatternAllowlist;
import com.freepark.local.domain.PatternAllowlistRepository;
import com.freepark.local.domain.PlateColor;
import com.freepark.local.domain.WhitelistVehicle;
import com.freepark.local.domain.WhitelistVehicleRepository;
import com.freepark.local.sitesettings.service.SystemSettingsService;

import tools.jackson.databind.JsonNode;

/**
 * 云端配置同步应用器：把订阅器收齐的一批帧（同一 snapshotId 内按 seq 有序）
 * 应用为本地七类配置（lot/lane/blacklist/pattern/whitelist/internal/space）。
 *
 * <p>约定与云端一致（edge.config.sync/3）：</p>
 * <ul>
 *   <li>full：整包快照。以快照帧清单为准——对每个出现在快照中的车场，各业务域
 *       “整域替换”：先清掉本地该车场下不在快照条目集内的行（含 cloud_id 为空的
 *       本地自建行），再按 cloud_id 逐条 upsert；快照中缺失的域视为云端为空并清空。
 *       本地有、快照中已无的车场（曾由云端下发管理）做整体清理（视为被摘除）。</li>
 *   <li>delta：变更增量。条目按 op=upsert（按 cloud_id 整条覆盖）/delete（按 cloud_id
 *       删除）精确应用；lot 域不使用 delete。</li>
 * </ul>
 *
 * <p>数据安全：不以表为单位重建，始终按实体行做 upsert/delete，保留本地 UUID 主键
 * 与既有外部引用；写入按「车场 × 域」或分片小批量提交，避免超大事务。</p>
 */
@Service
public class ConfigSyncApplyService {

    private static final Logger log = LoggerFactory.getLogger(ConfigSyncApplyService.class);

    static final String KIND_FULL = CloudConfigSyncSubscriber.KIND_FULL;
    static final String KIND_DELTA = CloudConfigSyncSubscriber.KIND_DELTA;

    private static final String DOMAIN_LOT = "lot";
    private static final String DOMAIN_BLACKLIST = "blacklist";
    private static final String DOMAIN_PATTERN = "pattern";
    private static final String DOMAIN_WHITELIST = "whitelist";
    private static final String DOMAIN_INTERNAL = "internal";
    private static final String DOMAIN_SPACE = "space";
    private static final String DOMAIN_LANE = "lane";

    private static final String TYPE_LOCATION = "location";
    private static final String TYPE_AREA = "area";
    private static final String TYPE_SPACE = "space";

    private static final String OP_UPSERT = "upsert";
    private static final String OP_DELETE = "delete";

    /** 单个业务域的写入分片：避免一条大事务持锁过久/内存过高 */
    private static final int UPSERT_CHUNK_SIZE = 500;

    private final ParkingLotRepository lots;
    private final ParkingLaneRepository lanes;
    private final BlacklistVehicleRepository blacklists;
    private final PatternAllowlistRepository patterns;
    private final WhitelistVehicleRepository whitelists;
    private final InternalVehicleRepository internals;
    private final ParkingSpaceRepository spaces;
    private final ParkingAreaRepository areas;
    private final ParkingLocationRepository locations;
    private final SystemSettingsService systemSettings;
    private final TransactionTemplate tx;

    public ConfigSyncApplyService(
            ParkingLotRepository lots,
            ParkingLaneRepository lanes,
            BlacklistVehicleRepository blacklists,
            PatternAllowlistRepository patterns,
            WhitelistVehicleRepository whitelists,
            InternalVehicleRepository internals,
            ParkingSpaceRepository spaces,
            ParkingAreaRepository areas,
            ParkingLocationRepository locations,
            SystemSettingsService systemSettings,
            PlatformTransactionManager txManager) {
        this.lots = lots;
        this.lanes = lanes;
        this.blacklists = blacklists;
        this.patterns = patterns;
        this.whitelists = whitelists;
        this.internals = internals;
        this.spaces = spaces;
        this.areas = areas;
        this.locations = locations;
        this.systemSettings = systemSettings;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 订阅器收齐一批帧后调用：按首帧 kind 分流 full/delta。 */
    public synchronized void applyFrames(List<JsonNode> frames) {
        if (frames == null || frames.isEmpty()) {
            return;
        }
        String kind = frames.getFirst().path("kind").asText("");
        if (KIND_FULL.equals(kind)) {
            applyFull(frames);
        } else if (KIND_DELTA.equals(kind)) {
            applyDelta(frames);
        } else {
            log.warn("配置同步批次忽略：未知 kind={}", kind);
        }
    }

    // ------------------------------------------------------------------
    // 全量快照
    // ------------------------------------------------------------------

    private void applyFull(List<JsonNode> frames) {
        // 1) 帧按车场代码分组（快照帧清单即“权威车场清单”）
        Map<String, List<JsonNode>> framesByCode = new LinkedHashMap<>();
        boolean emptyNodeSnapshot = false;
        for (JsonNode frame : frames) {
            String code = textOrNull(frame.path("lot"));
            if (code == null) {
                emptyNodeSnapshot = true;
            } else {
                framesByCode.computeIfAbsent(code, key -> new ArrayList<>()).add(frame);
            }
        }
        if (emptyNodeSnapshot && framesByCode.isEmpty()) {
            log.info("配置同步：收到空快照，节点名下已无车场，开始清理被摘除车场配置");
        }
        ZoneId zone = resolveZone();
        Set<String> managedCodes = framesByCode.keySet();

        // 2) 先保证快照内每个车场在本地的 ParkingLot 存在（lot 帧逐帧覆盖配置）
        Map<String, ParkingLot> lotByCode = new LinkedHashMap<>();
        for (Map.Entry<String, List<JsonNode>> entry : framesByCode.entrySet()) {
            String code = entry.getKey();
            JsonNode lotItem = findLotItem(entry.getValue());
            if (lotItem == null) {
                log.warn("配置同步：车场 {} 缺少 lot 基础帧，跳过该车场", code);
                continue;
            }
            final String fCode = code;
            ParkingLot lot = tx.execute(status -> {
                ParkingLot existing = lots.findByCode(fCode).orElse(null);
                if (existing == null) {
                    String name = requireText(lotItem, "name");
                    LotType lotType = parseLotType(textOrNull(lotItem.path("lotType")));
                    boolean enabled = lotItem.path("enabled").asBoolean(true);
                    existing = new ParkingLot(name, fCode, lotType, null, 0, enabled);
                }
                applyLotItem(existing, lotItem);
                return lots.save(existing);
            });
            if (lot != null) {
                lotByCode.put(code, lot);
            }
        }

        // 3) 每个车场逐域：清理 → upsert（顺序保证父级先建：space 三层）
        for (String code : lotByCode.keySet()) {
            ParkingLot lot = lotByCode.get(code);
            // 聚合该车场各域条目（快照中缺失的域视为云端为空）
            Map<String, List<JsonNode>> itemsByDomain = new LinkedHashMap<>();
            for (JsonNode frame : framesByCode.get(code)) {
                String domain = textOrNull(frame.path("domain"));
                if (domain == null || DOMAIN_LOT.equals(domain)) {
                    continue;
                }
                List<JsonNode> list = itemsByDomain.computeIfAbsent(domain, key -> new ArrayList<>());
                JsonNode items = frame.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        list.add(item);
                    }
                }
            }
            List<String> domains = List.of(DOMAIN_LANE, DOMAIN_BLACKLIST, DOMAIN_PATTERN,
                    DOMAIN_WHITELIST, DOMAIN_INTERNAL, DOMAIN_SPACE);
            for (String domain : domains) {
                List<JsonNode> items = itemsByDomain.getOrDefault(domain, List.of());
                applyDomainFull(lot, domain, items, zone);
            }
        }

        // 4) 清理“快照中已不存在”的车场：仅清理曾带云端数据（被云端托管过）的车场，
        //    避免误删本地自建（OFFLINE/独立运行）的车场。
        cleanupRemovedLots(managedCodes);

        log.info("配置同步：全量快照应用完成（{} 个车场，{} 帧）", lotByCode.size(), frames.size());
    }

    /** 在车场的 lot 帧中取基础配置条目（items 首元素）。 */
    private static JsonNode findLotItem(List<JsonNode> frames) {
        for (JsonNode frame : frames) {
            if (DOMAIN_LOT.equals(textOrNull(frame.path("domain")))) {
                JsonNode items = frame.path("items");
                if (items.isArray() && !items.isEmpty()) {
                    return items.get(0);
                }
            }
        }
        return null;
    }

    /** 单个车场单个业务域的全量替换：清理不在保留集内的行，再按 cloud_id upsert。 */
    private void applyDomainFull(ParkingLot lot, String domain, List<JsonNode> items, ZoneId zone) {
        // 保留集：本域本次快照携带的 cloud_id（space 域按 type 分别统计）
        Set<Long> retained = new LinkedHashSet<>();
        Set<Long> retainedLocations = new LinkedHashSet<>();
        Set<Long> retainedAreas = new LinkedHashSet<>();
        for (JsonNode item : items) {
            Long id = idOf(item);
            if (id == null) {
                continue;
            }
            if (DOMAIN_SPACE.equals(domain)) {
                String type = textOrNull(item.path("type"));
                if (TYPE_LOCATION.equals(type)) {
                    retainedLocations.add(id);
                } else if (TYPE_AREA.equals(type)) {
                    retainedAreas.add(id);
                } else {
                    retained.add(id);
                }
            } else {
                retained.add(id);
            }
        }
        // 先清理后 upsert：移除已删除/本地自建行，腾出唯一键（车牌/号码段等）避免插入冲突
        tx.executeWithoutResult(status -> cleanDomain(lot, domain, retained,
                retainedLocations, retainedAreas));
        if (DOMAIN_SPACE.equals(domain)) {
            applySpaceDomainFull(lot, items, retainedLocations, retainedAreas);
        } else {
            upsertDomainChunks(lot, domain, items, zone);
        }
    }

    /** 清理某车场某域不在保留集内的本地行（含 cloud_id 为空的自建行）。 */
    private void cleanDomain(ParkingLot lot, String domain, Set<Long> retained,
            Set<Long> retainedLocations, Set<Long> retainedAreas) {
        switch (domain) {
            case DOMAIN_BLACKLIST -> {
                List<BlacklistVehicle> rows = blacklists.findAllByLotId(lot.getId());
                rows.removeIf(r -> retained.contains(r.getCloudId()));
                if (!rows.isEmpty()) {
                    blacklists.deleteAll(rows);
                }
            }
            case DOMAIN_PATTERN -> {
                List<PatternAllowlist> rows = patterns.findAllByLotId(lot.getId());
                rows.removeIf(r -> retained.contains(r.getCloudId()));
                if (!rows.isEmpty()) {
                    patterns.deleteAll(rows);
                }
            }
            case DOMAIN_WHITELIST -> {
                List<WhitelistVehicle> rows = whitelists.findAllByLotId(lot.getId());
                rows.removeIf(r -> retained.contains(r.getCloudId()));
                if (!rows.isEmpty()) {
                    whitelists.deleteAll(rows);
                }
            }
            case DOMAIN_INTERNAL -> {
                List<InternalVehicle> rows = internals.findAllByLotId(lot.getId());
                rows.removeIf(r -> retained.contains(r.getCloudId()));
                if (!rows.isEmpty()) {
                    internals.deleteAll(rows);
                }
            }
            case DOMAIN_SPACE -> {
                // 车位/区域/位置 三层，先删子再删父；父不在保留集时其下子行一并删除
                List<ParkingSpace> spaceRows = spaces.findAllByLotId(lot.getId());
                spaceRows.removeIf(s -> retained.contains(s.getCloudId())
                        && (s.getArea().getCloudId() == null
                                || retainedAreas.contains(s.getArea().getCloudId())));
                List<ParkingArea> areaRows = areas.findByLocationLotIdOrderByNameAsc(lot.getId());
                areaRows.removeIf(a -> retainedAreas.contains(a.getCloudId())
                        && (a.getLocation().getCloudId() == null
                                || retainedLocations.contains(a.getLocation().getCloudId())));
                List<ParkingLocation> locationRows = locations.findByLotIdOrderByNameAsc(lot.getId());
                locationRows.removeIf(l -> retainedLocations.contains(l.getCloudId()));
                if (!spaceRows.isEmpty()) {
                    spaces.deleteAll(spaceRows);
                }
                if (!areaRows.isEmpty()) {
                    areas.deleteAll(areaRows);
                }
                if (!locationRows.isEmpty()) {
                    locations.deleteAll(locationRows);
                }
            }
            case DOMAIN_LANE -> {
                List<ParkingLane> laneRows = lanes.findAllByLot_IdOrderByCreatedAtAsc(lot.getId());
                laneRows.removeIf(r -> retained.contains(r.getCloudId()));
                if (!laneRows.isEmpty()) {
                    lanes.deleteAll(laneRows);
                }
            }
            default -> log.debug("配置同步清理跳过未知域 {}（lot={}）", domain, lot.getCode());
        }
    }

    /** space 域整域替换：分 type 阶段（location→area→space）逐片 upsert，保证父级先存在。 */
    private void applySpaceDomainFull(ParkingLot lot, List<JsonNode> items,
            Set<Long> retainedLocations, Set<Long> retainedAreas) {
        List<JsonNode> locItems = new ArrayList<>();
        List<JsonNode> areaItems = new ArrayList<>();
        List<JsonNode> spaceItems = new ArrayList<>();
        for (JsonNode item : items) {
            String type = textOrNull(item.path("type"));
            if (TYPE_LOCATION.equals(type)) {
                locItems.add(item);
            } else if (TYPE_AREA.equals(type)) {
                areaItems.add(item);
            } else {
                spaceItems.add(item);
            }
        }
        upsertChunks(lot, DOMAIN_SPACE, TYPE_LOCATION, locItems, retainedLocations, null);
        upsertChunks(lot, DOMAIN_SPACE, TYPE_AREA, areaItems, retainedAreas, retainedLocations);
        upsertChunks(lot, DOMAIN_SPACE, TYPE_SPACE, spaceItems, null, retainedAreas);
    }

    /** 小批量 upsert 通用入口（非 space 域）与 space 域 phase 入口共用。 */
    private void upsertDomainChunks(ParkingLot lot, String domain, List<JsonNode> items, ZoneId zone) {
        if (items.isEmpty()) {
            return;
        }
        for (int from = 0; from < items.size(); from += UPSERT_CHUNK_SIZE) {
            int to = Math.min(from + UPSERT_CHUNK_SIZE, items.size());
            List<JsonNode> chunk = items.subList(from, to);
            final String fDomain = domain;
            tx.executeWithoutResult(status -> {
                for (JsonNode item : chunk) {
                    upsertItem(lot, fDomain, item, null, null, zone);
                }
            });
        }
    }

    /** space 域单个 phase 的分片 upsert（phaseKind 由 type 传参）。 */
    private void upsertChunks(ParkingLot lot, String domain, String type,
            List<JsonNode> items, Set<Long> retainedSelf, Set<Long> retainedParent) {
        if (items.isEmpty()) {
            return;
        }
        for (int from = 0; from < items.size(); from += UPSERT_CHUNK_SIZE) {
            int to = Math.min(from + UPSERT_CHUNK_SIZE, items.size());
            List<JsonNode> chunk = items.subList(from, to);
            tx.executeWithoutResult(status -> {
                for (JsonNode item : chunk) {
                    upsertItem(lot, domain, item, type, retainedParent, null);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // 增量
    // ------------------------------------------------------------------

    private void applyDelta(List<JsonNode> frames) {
        ZoneId zone = resolveZone();
        int applied = 0;
        for (JsonNode frame : frames) {
            String code = textOrNull(frame.path("lot"));
            String domain = textOrNull(frame.path("domain"));
            if (code == null || domain == null) {
                continue;
            }
            ParkingLot lot = lots.findByCode(code).orElse(null);
            if (lot == null) {
                // 变更先于全量到达（重启后首帧即为增量）：等下一轮全量补齐，丢弃该帧增量
                log.warn("配置同步增量忽略：本地无车场 {}（域 {}），等待下一轮全量", code, domain);
                continue;
            }
            JsonNode items = frame.path("items");
            if (!items.isArray()) {
                continue;
            }
            for (JsonNode entry : items) {
                String op = textOrNull(entry.path("op"));
                if (OP_UPSERT.equals(op)) {
                    JsonNode item = entry.path("item");
                    if (item.isMissingNode() || item.isNull()) {
                        continue;
                    }
                    tx.executeWithoutResult(status -> {
                        if (DOMAIN_SPACE.equals(domain)) {
                            String type = textOrNull(item.path("type"));
                            upsertItem(lot, domain, item, type, null, zone);
                        } else {
                            upsertItem(lot, domain, item, null, null, zone);
                        }
                    });
                    applied++;
                } else if (OP_DELETE.equals(op)) {
                    Long id = entry.path("id").asLong(-1);
                    if (id > 0) {
                        tx.executeWithoutResult(status -> deleteByCloudId(lot, domain, id));
                        applied++;
                    }
                } else {
                    log.debug("配置同步增量忽略未知 op={}", op);
                }
            }
        }
        log.info("配置同步：增量批次应用完成（{} 条，{} 帧）", applied, frames.size());
    }

    /** 按 cloud_id 删除某域某行（lot 域不使用 delete）。 */
    private void deleteByCloudId(ParkingLot lot, String domain, Long id) {
        switch (domain) {
            case DOMAIN_BLACKLIST -> blacklists.findByCloudId(id).ifPresent(blacklists::delete);
            case DOMAIN_LANE -> lanes.findByCloudId(id).ifPresent(lanes::delete);
            case DOMAIN_PATTERN -> patterns.findByCloudId(id).ifPresent(patterns::delete);
            case DOMAIN_WHITELIST -> whitelists.findByCloudId(id).ifPresent(whitelists::delete);
            case DOMAIN_INTERNAL -> internals.findByCloudId(id).ifPresent(internals::delete);
            case DOMAIN_SPACE -> {
                ParkingSpace space = spaces.findByCloudId(id).orElse(null);
                if (space != null) {
                    spaces.delete(space);
                    return;
                }
                ParkingArea area = areas.findByCloudId(id).orElse(null);
                if (area != null) {
                    // 云端删除区域前必先删除其下泊位；此处防御性一并删除
                    List<ParkingSpace> children = spaces.findAllByLotId(lot.getId()).stream()
                            .filter(s -> s.getArea() != null
                                    && s.getArea().getId().equals(area.getId()))
                            .toList();
                    if (!children.isEmpty()) {
                        spaces.deleteAll(children);
                    }
                    areas.delete(area);
                    return;
                }
                ParkingLocation location = locations.findByCloudId(id).orElse(null);
                if (location != null) {
                    List<ParkingArea> areaChildren = areas.findByLocationIdOrderByNameAsc(location.getId());
                    for (ParkingArea child : areaChildren) {
                        List<ParkingSpace> spaceChildren = spaces.findAllByLotId(lot.getId()).stream()
                                .filter(s -> s.getArea() != null
                                        && s.getArea().getId().equals(child.getId()))
                                .toList();
                        if (!spaceChildren.isEmpty()) {
                            spaces.deleteAll(spaceChildren);
                        }
                    }
                    if (!areaChildren.isEmpty()) {
                        areas.deleteAll(areaChildren);
                    }
                    locations.delete(location);
                }
            }
            default -> log.debug("配置同步删除跳过未知域 {}（cloud_id={}）", domain, id);
        }
    }

    // ------------------------------------------------------------------
    // 被摘除车场清理
    // ------------------------------------------------------------------

    /** 清理“本次快照已不存在”且“曾托管云端数据”的本地车场（含其全部配置行）。 */
    private void cleanupRemovedLots(Set<String> managedCodes) {
        List<ParkingLot> allLots = lots.findAll();
        for (ParkingLot lot : allLots) {
            if (managedCodes.contains(lot.getCode())) {
                continue;
            }
            if (!hasCloudManagedRows(lot)) {
                log.debug("配置同步保留本地自建车场 {}（未被云端托管）", lot.getCode());
                continue;
            }
            log.info("配置同步：车场 {} 已不在快照内（被摘除），清理本地配置", lot.getCode());
            tx.executeWithoutResult(status -> {
                removeLotData(lot);
                try {
                    lots.delete(lot);
                } catch (RuntimeException ex) {
                    // 车场可能仍有运行期引用（如在场记录/车道），删除被数据库约束拦截时保留行但已清空配置
                    log.warn("配置同步：车场 {} 配置已清空但删除被拒绝（{}），保留空车场",
                            lot.getCode(), ex.getMessage());
                }
            });
        }
    }

    /** 车场下各同步业务表是否有任一行的 cloud_id 非空（即该车场是否曾被云端托管）。 */
    private boolean hasCloudManagedRows(ParkingLot lot) {
        UUID lotId = lot.getId();
        if (lanes.findAllByLot_IdOrderByCreatedAtAsc(lotId).stream().anyMatch(r -> r.getCloudId() != null)) {
            return true;
        }
        if (blacklists.findAllByLotId(lotId).stream().anyMatch(r -> r.getCloudId() != null)) {
            return true;
        }
        if (patterns.findAllByLotId(lotId).stream().anyMatch(r -> r.getCloudId() != null)) {
            return true;
        }
        if (whitelists.findAllByLotId(lotId).stream().anyMatch(r -> r.getCloudId() != null)) {
            return true;
        }
        if (internals.findAllByLotId(lotId).stream().anyMatch(r -> r.getCloudId() != null)) {
            return true;
        }
        if (spaces.findAllByLotId(lotId).stream().anyMatch(r -> r.getCloudId() != null)) {
            return true;
        }
        if (areas.findByLocationLotIdOrderByNameAsc(lotId).stream().anyMatch(r -> r.getCloudId() != null)) {
            return true;
        }
        return locations.findByLotIdOrderByNameAsc(lotId).stream().anyMatch(r -> r.getCloudId() != null);
    }

    /** 清空车场下全部同步配置行（先子后父，避免外键约束）。 */
    private void removeLotData(ParkingLot lot) {
        UUID lotId = lot.getId();
        List<ParkingLane> laneRows = lanes.findAllByLot_IdOrderByCreatedAtAsc(lotId);
        List<ParkingSpace> spaceRows = spaces.findAllByLotId(lotId);
        List<ParkingArea> areaRows = areas.findByLocationLotIdOrderByNameAsc(lotId);
        List<ParkingLocation> locationRows = locations.findByLotIdOrderByNameAsc(lotId);
        if (!laneRows.isEmpty()) {
            lanes.deleteAll(laneRows);
        }
        if (!spaceRows.isEmpty()) {
            spaces.deleteAll(spaceRows);
        }
        if (!areaRows.isEmpty()) {
            areas.deleteAll(areaRows);
        }
        if (!locationRows.isEmpty()) {
            locations.deleteAll(locationRows);
        }
        List<BlacklistVehicle> bl = blacklists.findAllByLotId(lotId);
        if (!bl.isEmpty()) {
            blacklists.deleteAll(bl);
        }
        List<PatternAllowlist> pa = patterns.findAllByLotId(lotId);
        if (!pa.isEmpty()) {
            patterns.deleteAll(pa);
        }
        List<WhitelistVehicle> wl = whitelists.findAllByLotId(lotId);
        if (!wl.isEmpty()) {
            whitelists.deleteAll(wl);
        }
        List<InternalVehicle> iv = internals.findAllByLotId(lotId);
        if (!iv.isEmpty()) {
            internals.deleteAll(iv);
        }
    }

    // ------------------------------------------------------------------
    // 单条目 upsert 映射
    // ------------------------------------------------------------------

    /** 单个条目的 upsert 分发（domain=space 时需传 itemType）。 */
    private void upsertItem(ParkingLot lot, String domain, JsonNode item,
            String spaceType, Set<Long> parentRetained, ZoneId zone) {
        if (DOMAIN_LOT.equals(domain)) {
            // lot 条目以 code 定位（无 numeric cloud_id），直接整条覆盖配置
            applyLotItem(lot, item);
            return;
        }
        Long id = idOf(item);
        if (id == null) {
            log.debug("配置同步条目缺少 cloud_id，跳过（lot={} domain={}）", lot.getCode(), domain);
            return;
        }
        switch (domain) {
            case DOMAIN_LANE -> upsertLane(lot, id, item);
            case DOMAIN_BLACKLIST -> upsertBlacklist(lot, id, item, zone);
            case DOMAIN_PATTERN -> upsertPattern(lot, id, item);
            case DOMAIN_WHITELIST -> upsertWhitelist(lot, id, item, zone);
            case DOMAIN_INTERNAL -> upsertInternal(lot, id, item);
            case DOMAIN_SPACE -> {
                String type = spaceType != null ? spaceType : textOrNull(item.path("type"));
                if (TYPE_LOCATION.equals(type)) {
                    upsertLocation(lot, id, item);
                } else if (TYPE_AREA.equals(type)) {
                    upsertArea(lot, id, item, parentRetained);
                } else if (TYPE_SPACE.equals(type)) {
                    upsertSpace(lot, id, item, parentRetained);
                } else {
                    log.debug("配置同步 space 条目未知 type={}", type);
                }
            }
            default -> log.debug("配置同步 upsert 跳过未知域 {}（lot={}）", domain, lot.getCode());
        }
    }

    private void upsertLane(ParkingLot lot, Long id, JsonNode item) {
        String name = requireText(item, "name");
        String code = requireText(item, "code");
        if (code.isEmpty()) {
            log.debug("配置同步 lane 条目缺少 code，跳过（cloud_id={} lot={}）", id, lot.getCode());
            return;
        }
        LaneType laneType = parseLaneType(textOrNull(item.path("laneType")));
        boolean enabled = item.path("enabled").asBoolean(true);
        // 双向通道的对向车场：能按编码解析到本地车场才建立关联，否则置空
        String linkedLotCode = textOrNull(item.path("linkedLotCode"));
        ParkingLot linkedLot = null;
        if (linkedLotCode != null) {
            linkedLot = lots.findByCode(linkedLotCode).orElse(null);
        }
        ParkingLane lane = lanes.findByCloudId(id).orElse(null);
        if (lane == null) {
            lane = new ParkingLane(lot, linkedLot, name, code, laneType, enabled);
            lane.setCloudId(id);
        } else {
            lane.updateDetails(lot, linkedLot, name, laneType, enabled);
        }
        lanes.save(lane);
    }

    private void upsertBlacklist(ParkingLot lot, Long id, JsonNode item, ZoneId zone) {
        String plateNumber = requireText(item, "plateNumber");
        PlateColor color = parseColor(textOrNull(item.path("plateColor")));
        String ownerName = requireText(item, "ownerName");
        String phone = textOrNull(item.path("phone"));
        String department = textOrNull(item.path("department"));
        String remark = textOrNull(item.path("remark"));
        Instant start = parseInstant(textOrNull(item.path("startTime")), zone);
        Instant end = parseInstant(textOrNull(item.path("endTime")), zone);
        boolean enabled = item.path("enabled").asBoolean(true);
        BlacklistVehicle vehicle = blacklists.findByCloudId(id).orElse(null);
        if (vehicle == null) {
            vehicle = new BlacklistVehicle(lot, plateNumber, color, ownerName, phone,
                    department, remark, start, end, enabled);
            vehicle.setCloudId(id);
        } else {
            vehicle.updateDetails(plateNumber, color, ownerName, phone, department,
                    remark, start, end, enabled);
        }
        blacklists.save(vehicle);
    }

    private void upsertPattern(ParkingLot lot, Long id, JsonNode item) {
        String name = requireText(item, "name");
        String pattern = requireText(item, "pattern");
        String remark = textOrNull(item.path("remark"));
        boolean enabled = item.path("enabled").asBoolean(true);
        PatternAllowlist rule = patterns.findByCloudId(id).orElse(null);
        if (rule == null) {
            rule = new PatternAllowlist(lot, name, pattern, remark, enabled);
            rule.setCloudId(id);
        } else {
            rule.updateDetails(name, pattern, remark, enabled);
        }
        patterns.save(rule);
    }

    private void upsertWhitelist(ParkingLot lot, Long id, JsonNode item, ZoneId zone) {
        String plateNumber = requireText(item, "plateNumber");
        PlateColor color = parseColor(textOrNull(item.path("plateColor")));
        String ownerName = requireText(item, "ownerName");
        VehicleType type = parseVehicleType(textOrNull(item.path("type")));
        String phone = textOrNull(item.path("phone"));
        String department = textOrNull(item.path("department"));
        String remark = textOrNull(item.path("remark"));
        Instant start = parseInstant(textOrNull(item.path("startTime")), zone);
        Instant end = parseInstant(textOrNull(item.path("endTime")), zone);
        boolean enabled = item.path("enabled").asBoolean(true);
        WhitelistVehicle vehicle = whitelists.findByCloudId(id).orElse(null);
        if (vehicle == null) {
            vehicle = new WhitelistVehicle(lot, plateNumber, color, ownerName, type, phone,
                    department, remark, start, end, enabled);
            vehicle.setCloudId(id);
        } else {
            vehicle.updateDetails(plateNumber, color, ownerName, type, phone, department,
                    remark, start, end, enabled);
        }
        whitelists.save(vehicle);
    }

    private void upsertInternal(ParkingLot lot, Long id, JsonNode item) {
        String plateNumber = requireText(item, "plateNumber");
        PlateColor color = parseColor(textOrNull(item.path("plateColor")));
        String ownerName = requireText(item, "ownerName");
        VehicleType type = parseVehicleType(textOrNull(item.path("type")));
        String phone = textOrNull(item.path("phone"));
        String department = textOrNull(item.path("department"));
        String remark = textOrNull(item.path("remark"));
        String batchIdText = textOrNull(item.path("batchId"));
        UUID batchId = batchIdText == null ? null : parseUuid(batchIdText);
        boolean enabled = item.path("enabled").asBoolean(true);
        InternalVehicle vehicle = internals.findByCloudId(id).orElse(null);
        if (vehicle == null) {
            vehicle = new InternalVehicle(lot, plateNumber, color, ownerName, type, phone,
                    department, remark, enabled);
            vehicle.setCloudId(id);
            vehicle.setBatchId(batchId);
        } else {
            vehicle.updateDetails(plateNumber, color, ownerName, type, phone, department,
                    remark, enabled);
            vehicle.setBatchId(batchId);
        }
        internals.save(vehicle);
    }

    private void upsertLocation(ParkingLot lot, Long id, JsonNode item) {
        String name = requireText(item, "name");
        ParkingLocation location = locations.findByCloudId(id).orElse(null);
        if (location == null) {
            location = new ParkingLocation(lot, name);
            location.setCloudId(id);
        } else {
            location.rename(name);
            if (location.getLot() == null || !location.getLot().getId().equals(lot.getId())) {
                location.attachTo(lot);
            }
        }
        locations.save(location);
    }

    private void upsertArea(ParkingLot lot, Long id, JsonNode item, Set<Long> parentRetained) {
        Long locationId = longOrNull(item.path("locationId"));
        if (locationId == null) {
            return;
        }
        ParkingLocation location = locations.findByCloudId(locationId).orElse(null);
        if (location == null) {
            if (parentRetained == null || !parentRetained.contains(locationId)) {
                // 父级不在本域保留集：异常数据或父级将于本域清理时被移除，跳过
                return;
            }
            return;
        }
        String name = requireText(item, "name");
        ParkingArea area = areas.findByCloudId(id).orElse(null);
        if (area == null) {
            area = new ParkingArea(location, name);
            area.setCloudId(id);
        } else {
            area.rename(name);
            if (area.getLocation() == null || !area.getLocation().getId().equals(location.getId())) {
                area.attachTo(location);
            }
        }
        areas.save(area);
    }

    private void upsertSpace(ParkingLot lot, Long id, JsonNode item, Set<Long> parentRetained) {
        Long areaId = longOrNull(item.path("areaId"));
        if (areaId == null) {
            return;
        }
        ParkingArea area = areas.findByCloudId(areaId).orElse(null);
        if (area == null) {
            if (parentRetained == null || !parentRetained.contains(areaId)) {
                return;
            }
            return;
        }
        String code = requireText(item, "code");
        boolean enabled = item.path("enabled").asBoolean(true);
        ParkingSpace space = spaces.findByCloudId(id).orElse(null);
        if (space == null) {
            space = new ParkingSpace(lot, area, code, enabled);
            space.setCloudId(id);
        } else {
            space.updateDetails(area, code, enabled);
        }
        spaces.save(space);
    }

    /** lot 基础配置覆盖：云端条目只含其暴露字段，其余本地字段（地址/总泊位/地图等）保持原值。 */
    private void applyLotItem(ParkingLot lot, JsonNode item) {
        String name = requireText(item, "name");
        LotType lotType = parseLotType(textOrNull(item.path("lotType")));
        boolean enabled = item.path("enabled").asBoolean(true);
        lot.updateDetails(name, lotType, lot.getAddress(), lot.getTotalSpaces(), enabled);
        List<InterceptRuleType> entryRules = new ArrayList<>();
        List<InterceptRuleType> exitRules = new ArrayList<>();
        if (item.path("entryInterceptArrears").asBoolean(false)) {
            entryRules.add(InterceptRuleType.ARREARS);
        }
        if (item.path("entryInterceptBlacklist").asBoolean(false)) {
            entryRules.add(InterceptRuleType.BLACKLIST);
        }
        if (item.path("exitInterceptArrears").asBoolean(false)) {
            exitRules.add(InterceptRuleType.ARREARS);
        }
        if (item.path("exitInterceptBlacklist").asBoolean(false)) {
            exitRules.add(InterceptRuleType.BLACKLIST);
        }
        lot.updateInterceptRules(entryRules, exitRules);
        JsonNode judgmentOrder = item.path("judgmentOrder");
        if (judgmentOrder.isArray() && !judgmentOrder.isEmpty()) {
            List<AccessJudgmentRuleType> order = new ArrayList<>();
            for (JsonNode node : judgmentOrder) {
                AccessJudgmentRuleType rule = parseJudgment(textOrNull(node));
                if (rule != null) {
                    order.add(rule);
                }
            }
            if (!order.isEmpty()) {
                lot.updateAccessJudgmentOrder(order);
            }
        }
    }

    // ------------------------------------------------------------------
    // 解析工具
    // ------------------------------------------------------------------

    private ZoneId resolveZone() {
        try {
            return systemSettings.getTimezone();
        } catch (RuntimeException ex) {
            return ZoneId.systemDefault();
        }
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String requireText(JsonNode item, String key) {
        String value = textOrNull(item.path(key));
        return value == null ? "" : value;
    }

    private static Long idOf(JsonNode item) {
        JsonNode node = item.path("id");
        if (!node.isMissingNode() && !node.isNull() && node.isIntegralNumber()) {
            return node.asLong();
        }
        return null;
    }

    private static Long longOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isIntegralNumber()) {
            return null;
        }
        return node.asLong();
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Instant parseInstant(String text, ZoneId zone) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text).atZone(zone).toInstant();
        } catch (Exception ignored) {
            // fallthrough
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static PlateColor parseColor(String name) {
        if (name == null) {
            return PlateColor.BLUE;
        }
        try {
            return PlateColor.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return PlateColor.BLUE;
        }
    }

    private static VehicleType parseVehicleType(String name) {
        if (name == null) {
            return VehicleType.OTHER;
        }
        try {
            return VehicleType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return VehicleType.OTHER;
        }
    }

    private static LotType parseLotType(String name) {
        if (name == null) {
            return LotType.INTERNAL;
        }
        try {
            return LotType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return LotType.INTERNAL;
        }
    }

    private static LaneType parseLaneType(String name) {
        if (name == null) {
            return LaneType.ENTRANCE;
        }
        try {
            return LaneType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return LaneType.ENTRANCE;
        }
    }

    private static AccessJudgmentRuleType parseJudgment(String name) {
        if (name == null) {
            return null;
        }
        try {
            return AccessJudgmentRuleType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
