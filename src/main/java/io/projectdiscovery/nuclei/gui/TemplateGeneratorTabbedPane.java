/*
 * MIT License
 *
 * Copyright (c) 2021 ProjectDiscovery, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package io.projectdiscovery.nuclei.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

public final class TemplateGeneratorTabbedPane extends JTabbedPane {
    private final List<TemplateGeneratorTab> templateGeneratorTabs;

    private int openedTabCounter = 1;

    TemplateGeneratorTabbedPane(GeneralSettings generalSettings) {
        this(generalSettings, new ArrayList<>(), () -> {
        });
    }

    TemplateGeneratorTabbedPane(GeneralSettings generalSettings, Runnable closeAction) {
        this(generalSettings, new ArrayList<>(), closeAction);
    }

    private TemplateGeneratorTabbedPane(GeneralSettings generalSettings, List<TemplateGeneratorTab> templateGeneratorTabs, Runnable closeAction) {
        super(TOP, SCROLL_TAB_LAYOUT);
        this.templateGeneratorTabs = templateGeneratorTabs;

        this.addChangeListener(e -> {
            if (((JTabbedPane) e.getSource()).getTabCount() == 0) {
                cleanup();
                closeAction.run();
            }
        });

        this.setTabLayoutPolicy(SCROLL_TAB_LAYOUT);

        this.addMouseWheelListener(this::navigateTabsWithMouseScroll);
        this.addMouseListener(closeTabWithMiddleMouseButtonAdapter());

        this.setVisible(true);
    }

    public void cleanup() {
        Executors.newSingleThreadExecutor().submit(() -> this.templateGeneratorTabs.forEach(TemplateGeneratorTab::cleanup));
        this.removeAll();
    }

    public void addTab(TemplateGeneratorTab templateGeneratorTab) {
        this.templateGeneratorTabs.add(templateGeneratorTab);
        final String tabName = Optional.ofNullable(templateGeneratorTab.getName())
                .orElseGet(() -> "Tab " + this.openedTabCounter++);
        templateGeneratorTab.setName(tabName);

        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabPanel.setOpaque(false);
        JButton closeButton = new JButton("x");
        closeButton.setMargin(new Insets(0, 5, 0, 5));
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusPainted(false);
        closeButton.setOpaque(false);
        closeButton.addActionListener(e -> {
            int index = this.indexOfTabComponent(tabPanel);
            if (index != -1) {
                this.remove(index);
            }
        });

        JLabel tabLabel = new JLabel(tabName);
        // Create a JTextField for editing the tab name, initially hidden
        JTextField tabNameEditor = new JTextField(tabName);
        tabNameEditor.setVisible(false);
        // Add action listener to handle renaming when the user presses Enter or loses focus
        tabNameEditor.addActionListener(e -> finishEditingTabName(templateGeneratorTab, tabLabel, tabNameEditor, tabPanel));
        tabNameEditor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                finishEditingTabName(templateGeneratorTab, tabLabel, tabNameEditor, tabPanel);
            }
        });

        tabLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    // Handle single click: set focus to this tab
                    int index = indexOfTabComponent(tabPanel);
                    if (index != -1) {
                        setSelectedIndex(index);
                    }
                } else if (e.getClickCount() == 2) {
                    // Handle double click: enable editing the tab name
                    tabLabel.setVisible(false);
                    tabNameEditor.setVisible(true);
                    tabNameEditor.requestFocus();
                    tabNameEditor.selectAll();
                }
            }
        });

        tabPanel.add(tabLabel);
        tabPanel.add(tabNameEditor);
        tabPanel.add(closeButton);

        this.addTab(tabName, templateGeneratorTab);
        this.setTabComponentAt(this.getTabCount() - 1, tabPanel);
        this.setSelectedIndex(this.getTabCount() - 1);
    }

    // Method to finish editing the tab name
    private void finishEditingTabName(TemplateGeneratorTab templateGeneratorTab, JLabel tabLabel, JTextField tabNameEditor, JPanel tabPanel) {
        String newTabName = tabNameEditor.getText().trim();
        if (!newTabName.isEmpty()) {
            templateGeneratorTab.setName(newTabName);  // Update the tab's model name
            tabLabel.setText(newTabName);  // Update the tab label
            this.setTitleAt(this.indexOfTabComponent(tabPanel), newTabName);  // Update the tab title
        }
        tabNameEditor.setVisible(false);  // Hide the editor
        tabLabel.setVisible(true);  // Show the label
    }


    @Override
    public void insertTab(String title, Icon icon, Component component, String tip, int index) {
        if (component.getClass().isAssignableFrom(TemplateGeneratorTab.class)) {
            super.insertTab(title, icon, component, tip, index);
        } else {
            throw new IllegalStateException("Programming error: Incorrect JTabbedPane.addTab() method invoked");
        }
    }

    public Optional<TemplateGeneratorTab> getTab(String tabName) {
        return this.templateGeneratorTabs.stream().filter(tab -> tab.getName().equals(tabName)).findAny();
    }

    public List<TemplateGeneratorTab> getTabs() {
        return this.templateGeneratorTabs;
    }

    @Override
    public void remove(int index) {
        if (index >= 0) {
            Executors.newSingleThreadExecutor().submit(() -> this.templateGeneratorTabs.get(index).cleanup());
            super.remove(index);
            this.templateGeneratorTabs.remove(index);
        }
    }

    @Override
    public void removeAll() {
        super.removeAll();
        this.templateGeneratorTabs.clear();
    }

    private void navigateTabsWithMouseScroll(MouseWheelEvent e) {
        final int selectedIndex = this.getSelectedIndex();

        int newIndex = e.getWheelRotation() < 0 ? selectedIndex - 1
                                                : selectedIndex + 1;

        final int tabCount = this.getTabCount();
        if (newIndex >= tabCount) {
            newIndex = 0;
        } else if (newIndex < 0) {
            newIndex = tabCount - 1;
        }

        this.setSelectedIndex(newIndex);
    }

    private MouseAdapter closeTabWithMiddleMouseButtonAdapter() {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON2) {
                    removeTab();
                } else {
                    super.mouseClicked(e);
                }
            }
        };
    }

    private void removeTab() {
        this.remove(this.getSelectedIndex());
    }
}
