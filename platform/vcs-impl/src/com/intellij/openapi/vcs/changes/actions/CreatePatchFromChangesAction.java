/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ExtendableAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import com.intellij.openapi.vcs.changes.ui.SessionDialog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class CreatePatchFromChangesAction extends ExtendableAction implements DumbAware {
  private static final ExtensionPointName<AnActionExtensionProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction.ExtensionProvider");

  public CreatePatchFromChangesAction() {
    super(EP_NAME);
  }

  public void defaultActionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if ((changes == null) || (changes.length == 0)) return;
    String commitMessage = null;
    List<ShelvedChangeList> shelvedChangeLists = ShelvedChangesViewManager.getShelvedLists(e.getDataContext());
    if (!shelvedChangeLists.isEmpty()) {
      commitMessage = shelvedChangeLists.get(0).DESCRIPTION;
    }
    else {
      ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
      if (changeLists != null && changeLists.length > 0) {
        commitMessage = changeLists [0].getComment();
      }
    }
    if (commitMessage == null) {
      commitMessage = e.getData(VcsDataKeys.PRESET_COMMIT_MESSAGE);
    }
    if (commitMessage == null) {
      commitMessage = "";
    }
    List<Change> changeCollection = new ArrayList<>();
    Collections.addAll(changeCollection, changes);
    createPatch(project, commitMessage, changeCollection);
  }

  public static void createPatch(Project project, String commitMessage, @NotNull List<Change> changeCollection) {
    project = project == null ? ProjectManager.getInstance().getDefaultProject() : project;
    final CreatePatchCommitExecutor executor = CreatePatchCommitExecutor.getInstance(project);
    CommitSession commitSession = executor.createCommitSession();
    if (commitSession instanceof CommitSessionContextAware) {
      ((CommitSessionContextAware)commitSession).setContext(new CommitContext());
    }
    DialogWrapper sessionDialog = new SessionDialog(executor.getActionText(),
                                                    project,
                                                    commitSession,
                                                    changeCollection,
                                                    commitMessage);
    if (!sessionDialog.showAndGet()) {
      return;
    }
    preloadContent(project, changeCollection);

    commitSession.execute(changeCollection, commitMessage);
  }

  private static void preloadContent(final Project project, final List<Change> changes) {
    // to avoid multiple progress dialogs, preload content under one progress
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        for(Change change: changes) {
          checkLoadContent(change.getBeforeRevision());
          checkLoadContent(change.getAfterRevision());
        }
      }

      private void checkLoadContent(final ContentRevision revision) {
        ProgressManager.checkCanceled();
        if (revision != null && !(revision instanceof BinaryContentRevision)) {
          try {
            revision.getContent();
          }
          catch (VcsException e1) {
            // ignore at the moment
          }
        }
      }
    }, VcsBundle.message("create.patch.loading.content.progress"), true, project);
  }

  public void defaultUpdate(@NotNull AnActionEvent e) {
    final Boolean haveSelectedChanges = e.getData(VcsDataKeys.HAVE_SELECTED_CHANGES);
    Change[] changes;
    ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
    List<ShelvedChangeList> shelveChangelists = ShelvedChangesViewManager.getShelvedLists(e.getDataContext());
    int changelistNum = changeLists == null ? 0 : changeLists.length;
    changelistNum += shelveChangelists.size();

    e.getPresentation().setEnabled(Boolean.TRUE.equals(haveSelectedChanges) && (changelistNum == 1) &&
                                   ((changes = e.getData(VcsDataKeys.CHANGES)) != null && changes.length > 0));
  }
}
