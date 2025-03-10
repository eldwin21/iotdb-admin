package org.apache.iotdb.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.iotdb.admin.model.entity.Measurement;
import org.springframework.stereotype.Component;

/**
 * @anthor fyx 2021/6/16
 */
@Component
public interface MeasurementMapper extends BaseMapper<Measurement> {
}
