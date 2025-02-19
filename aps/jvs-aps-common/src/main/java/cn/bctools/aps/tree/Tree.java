package cn.bctools.aps.tree;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharPool;
import cn.hutool.core.text.CharSequenceUtil;
import lombok.Data;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author jvs
 */
@Data
public class Tree<T> {

    /**
     * 层级
     */
    Integer level;

    /**
     * 节点
     */
    T node;

    /**
     * 子节点
     */
    List<Tree<T>> children;

    public void addChildren(Tree<T> child) {
        this.children = Optional.ofNullable(this.getChildren()).orElseGet(ArrayList::new);
        this.children.add(child);
    }

    /**
     * 树结构转字符串
     *
     * @param nameFun 节点名函数
     * @param idFun 节点id函数
     * @return 树结构字符串
     */
    public String treeToString(Function<Tree<T>, String> nameFun, Function<Tree<T>, String> idFun) {
        final StringWriter stringWriter = new StringWriter();
        printTree(this, new PrintWriter(stringWriter), 0, nameFun, idFun);
        return stringWriter.toString();
    }

    private void printTree(Tree<T> tree, PrintWriter writer, int intent, Function<Tree<T>, String> nameFun, Function<Tree<T>, String> idFun) {
        writer.println(CharSequenceUtil.format("{}{}[{}]", CharSequenceUtil.repeat(CharPool.SPACE, intent), nameFun.apply(tree), idFun.apply(tree)));
        writer.flush();
        final List<Tree<T>> childrenList = tree.getChildren();
        if (CollUtil.isNotEmpty(childrenList)) {
            for (Tree<T> child : childrenList) {
                printTree(child, writer, intent + 2, nameFun, idFun);
            }
        }
    }
}
