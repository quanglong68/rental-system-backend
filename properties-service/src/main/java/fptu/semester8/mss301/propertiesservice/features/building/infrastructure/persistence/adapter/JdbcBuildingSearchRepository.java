package fptu.semester8.mss301.propertiesservice.features.building.infrastructure.persistence.adapter;

import fptu.semester8.mss301.propertiesservice.features.building.application.model.BuildingSearchItem;
import fptu.semester8.mss301.propertiesservice.features.building.application.port.out.BuildingSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class JdbcBuildingSearchRepository implements BuildingSearchRepository {
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Page<BuildingSearchItem> search(String keyword, Long provinceId, Long wardId, Pageable pageable) {
        return searchScoped(keyword, provinceId, wardId, null, pageable);
    }

    @Override
    public Page<BuildingSearchItem> searchScoped(String keyword, Long provinceId, Long wardId,
                                                  Set<Long> buildingIds, Pageable pageable) {
        if (buildingIds != null && buildingIds.isEmpty()) return Page.empty(pageable);
        StringBuilder where = new StringBuilder(" WHERE b.is_deleted = false");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (buildingIds != null) {
            where.append(" AND b.building_id IN (:buildingIds)");
            params.addValue("buildingIds", buildingIds);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (b.name ILIKE :keyword ESCAPE '!' OR b.address ILIKE :keyword ESCAPE '!')");
            String escaped = keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            params.addValue("keyword", "%" + escaped + "%");
        }
        if (provinceId != null) {
            where.append(" AND w.province_id = :provinceId");
            params.addValue("provinceId", provinceId);
        }
        if (wardId != null) {
            where.append(" AND b.ward_id = :wardId");
            params.addValue("wardId", wardId);
        }
        String from = " FROM building b JOIN ward w ON w.ward_id = b.ward_id JOIN province p ON p.province_id = w.province_id" + where;
        return queryPage(from, params, pageable, null, null);
    }

    @Override
    public Page<BuildingSearchItem> nearby(BigDecimal latitude, BigDecimal longitude, double radiusMeters, Pageable pageable) {
        String distance = "ST_Distance(b.location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography)";
        String where = " WHERE b.is_deleted = false AND ST_DWithin(b.location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radiusMeters)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("latitude", latitude).addValue("longitude", longitude).addValue("radiusMeters", radiusMeters);
        String from = " FROM building b JOIN ward w ON w.ward_id = b.ward_id JOIN province p ON p.province_id = w.province_id" + where;
        return queryPage(from, params, pageable, distance, "distanceMeters");
    }

    private Page<BuildingSearchItem> queryPage(String from, MapSqlParameterSource params, Pageable pageable,
                                                String distanceExpression, String distanceAlias) {
        String select = "SELECT b.building_id, b.name, b.address, b.ward_id, w.name AS ward_name, " +
                "w.province_id, p.name AS province_name, b.latitude, b.longitude, b.description, b.building_status, " +
                "b.created_at, b.updated_at, " +
                "(SELECT bi.image_url FROM building_images bi WHERE bi.building_id = b.building_id " +
                "ORDER BY bi.display_order, bi.building_image_id LIMIT 1) AS building_url_image" +
                (distanceExpression == null ? "" : ", " + distanceExpression + " AS " + distanceAlias);
        String order = pageable.getSort().isUnsorted() ? "b.created_at DESC" : pageable.getSort().stream()
                .map(o -> (o.getProperty().equals("distanceMeters") && distanceExpression != null
                        ? distanceExpression
                        : "b." + switch (o.getProperty()) {
                            case "name" -> "name";
                            case "updatedAt" -> "updated_at";
                            default -> "created_at";
                        }) + " " + o.getDirection().name())
                .collect(java.util.stream.Collectors.joining(", "));
        String sql = select + from + " ORDER BY " + order + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<BuildingSearchItem> items = jdbc.query(sql, params, (rs, row) -> new BuildingSearchItem(
                rs.getLong("building_id"), rs.getString("name"), rs.getString("address"), rs.getLong("ward_id"),
                rs.getString("ward_name"), rs.getLong("province_id"), rs.getString("province_name"),
                rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getString("description"), rs.getString("building_status"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                distanceAlias == null ? null : rs.getDouble(distanceAlias), rs.getString("building_url_image")));
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, params, Long.class);
        return new PageImpl<>(items, pageable, total == null ? 0 : total);
    }
}
