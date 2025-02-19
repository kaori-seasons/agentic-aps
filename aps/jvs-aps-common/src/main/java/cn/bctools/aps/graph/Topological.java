package cn.bctools.aps.graph;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Set;

/**
 * @author jvs
 * 拓扑排序参数
 */
@Data
@Accessors(chain = true)
public class Topological {

    /**
     * 图节点id
     */
    private String nodeId;
    /**
     * 没有入度的节点id集合
     */
    private Set<String> noInDegreeNodeIds;

}
