package com.acme.json.helper.ui;

import cn.hutool.core.lang.Opt;
import com.acme.json.helper.ui.editor.CustomizeEditorFactory;
import com.acme.json.helper.ui.editor.Editor;
import com.acme.json.helper.ui.editor.enums.SupportedLanguages;
import com.acme.json.helper.ui.panel.JsonTreePanel;
import com.acme.json.helper.ui.panel.MainPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * json-helper工具窗口
 * @author 拒绝者
 * @date 2025-01-18
 */
public class MainToolWindowFactory implements ToolWindowFactory, DumbAware {
    /** 标签计数器 */
    private static final AtomicInteger tabCounter = new AtomicInteger(0);
    /** 加载资源文件 */
    private static final ResourceBundle bundle = ResourceBundle.getBundle("messages.JsonHelperBundle");

    /**
     * 创建工具窗口内容
     * @param project    项目
     * @param toolWindow 工具窗口
     */
    @Override
    public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // 创建初始页签
            this.createNewTab(project, toolWindow);
            // 绑定活动分组到窗口
            toolWindow.setTitleActions(List.of(this.createActionGroup(project, toolWindow)));
        });
    }

    /**
     * 创建新的页签
     * @param project    项目
     * @param toolWindow 工具窗口
     */
    public void createNewTab(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
        // 增加页签号数
        final int number = tabCounter.incrementAndGet();
        // 创建页签内容面板
        final JPanel contentPanel = this.createWindowContent(project, number);
        // 创建页签内容
        final Content content = ContentFactory.getInstance().createContent(contentPanel, String.valueOf(number), Boolean.FALSE);
        content.setCloseable(Boolean.TRUE);
        // 页签关闭时释放资源
        content.setDisposer(() -> {
            // 销毁所有窗口组件
            Arrays.stream(contentPanel.getComponents())
                    .filter(Objects::nonNull)
                    .filter(EditorTextField.class::isInstance)
                    .map(EditorTextField.class::cast)
                    .map(EditorTextField::getEditor).filter(Objects::nonNull)
                    .forEach(editor -> EditorFactory.getInstance().releaseEditor(editor));
            // 所有页签关闭后重置计数器
            ApplicationManager.getApplication().invokeLater(() ->
                    Opt.of(toolWindow.getContentManager().getContentCount() == 0)
                            .filter(i -> i)
                            .ifPresent(item -> tabCounter.set(0))
            );
        });
        // 将页签内容添加到工具窗口
        toolWindow.getContentManager().addContent(content);
        // 切换焦点到新页签
        ApplicationManager.getApplication().invokeLater(() -> toolWindow.getContentManager().setSelectedContent(content, Boolean.TRUE));
    }

    /**
     * 创建活动分组
     * @param project    项目
     * @param toolWindow 工具窗口
     * @return {@link DefaultActionGroup }
     */
    private DefaultActionGroup createActionGroup(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
        final DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new AnAction(bundle.getString("json.new.tab"), bundle.getString("json.new.tab.desc"), AllIcons.General.Add) {
            @Override
            public void actionPerformed(@NotNull final AnActionEvent e) {
                // 检查项目和窗口是否有效
                if (project.isDisposed() || toolWindow.isDisposed()) return;
                // 创建新页签
                MainToolWindowFactory.this.createNewTab(project, toolWindow);
            }
        });
        return actionGroup;
    }

    /**
     * 创建窗口内容
     * @param project 项目
     * @param number  页签号数
     * @return {@link JPanel }
     */
    private JPanel createWindowContent(@NotNull final Project project, final int number) {
        // 窗口工具
        final JPanel toolWindow = new JPanel(new BorderLayout(0, 0));
        // 创建JSON编辑器
        final EditorTextField editor = new CustomizeEditorFactory(SupportedLanguages.JSON, "Dummy_%d.json".formatted(number)).create(project);
        // 等待编辑器初始化后，挂载面板功能
        ApplicationManager.getApplication().invokeLater(() -> {
            // JSON编辑框绑定拖放监听
            Editor.bindDragAndDropListening(editor);
            // 组合布局
            toolWindow.setBorder(BorderFactory.createEmptyBorder());
            toolWindow.add(this.createSynthesisPanel(editor), BorderLayout.CENTER);
            // 重新绘制窗口
            toolWindow.revalidate();
            toolWindow.repaint();
        });
        return toolWindow;
    }

    /**
     * 创建合成面板
     *
     * @param editor 编辑器
     * @return {@link JPanel }
     */
    private JPanel createSynthesisPanel(final EditorTextField editor) {
        // 创建合成面板
        final JPanel panel = new JPanel(new BorderLayout(0, 0));
        // 主面板
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.add(new MainPanel().create(editor), BorderLayout.NORTH);
        // 创建滑动分区区块
        final JSplitPane editorTreeSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                editor,
                // 树面板
                new JsonTreePanel().create(editor)
        );
        // 自定义分隔条样式
        editorTreeSplit.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(final Graphics g) {
                        final int height = this.getHeight();
                        if (g instanceof final Graphics2D g2d) {
                            final Color color = UIManager.getColor("Component.borderColor");
                            g2d.setColor(color);
                            g2d.fillRect(0, 0, this.getWidth(), height);
                            g2d.setPaint(new GradientPaint(0, 0, color, 0, height, color));
                        }
                    }

                    @Override
                    public Dimension getPreferredSize() {
                        return new Dimension(8, 8);
                    }
                };
            }
        });
        // 初始比例
        editorTreeSplit.setDividerSize(8);
        editorTreeSplit.setResizeWeight(1);
        // 拖动时实时更新
        editorTreeSplit.setContinuousLayout(Boolean.TRUE);
        editorTreeSplit.setBorder(BorderFactory.createEmptyBorder());
        // 初始化分割窗格布局
        editorTreeSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                MainToolWindowFactory.this.initSplitPaneLayout(editorTreeSplit);
            }
        });
        panel.add(editorTreeSplit, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 初始化分割窗格布局
     *
     * @param splitPane 拆分窗格
     */
    private void initSplitPaneLayout(final JSplitPane splitPane) {
        // 延迟计算布局（确保父容器尺寸已确定）
        ApplicationManager.getApplication().invokeLater(() ->
                // 设置分隔条初始位置（底部组件显示最小高度）
                splitPane.setDividerLocation(
                        splitPane.getHeight() - splitPane.getDividerSize() - splitPane.getBottomComponent().getMinimumSize().height
                )
        );
    }
}