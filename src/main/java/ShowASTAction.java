import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import javafx.util.Pair;
import kotlin.Triple;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

import static java.lang.Integer.max;

public class ShowASTAction extends AnAction {

    private HashMap<PsiElement, Pair<Integer, Integer>> astNodeDfsTimeInTimeOut;

    @Override
    public void update(@NotNull final AnActionEvent e) {
        // Get required data keys
        final Project project = e.getProject();
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        //Set visibility only in case of existing project and editor and if a selection exists
        e.getPresentation().setEnabled( project != null
                && editor != null
                && editor.getSelectionModel().hasSelection() );
    }

    private void addSpaces(StringBuilder s, int n)
    {
        s.append(" ".repeat(Math.max(0, n)));
    }

    private String getStringFromTree(PsiElement[] elements, int off)
    {
        final StringBuilder s = new StringBuilder();
            for (PsiElement element : elements) {
                if (!element.getText().equals(" "))
                {
                    addSpaces(s, off);
                    s.append(element.getText()).append(" : ").append(element.getNode().getElementType().toString()).append('\n');
                }
                s.append(getStringFromTree(element.getChildren(), off + 8));
        }

        return s.toString();
    }

    @NotNull
    private String getStringFromTree(PsiElement[] elements)
    {
        return getStringFromTree(elements, 0);
    }

    @NotNull
    @Contract("_ -> new")
    private Triple<Integer, Integer, Integer> getVariablesCount(@NotNull PsiElement[] elements)
    {
        int variablesCount = 0;
        int referencesCount = 0;
        int exceptionsCount = 0;
        for (PsiElement element : elements) {
            if (element.getNode().getElementType().toString().equals("LOCAL_VARIABLE") ||
                    element.getNode().getElementType().toString().equals("FIELD"))
                variablesCount++;
            if (element.getNode().getElementType().toString().equals("REFERENCE_EXPRESSION"))
                referencesCount++;
            if (element.getNode().getElementType().toString().equals("THROW_STATEMENT"))
                exceptionsCount++;
            Triple<Integer, Integer, Integer> result = getVariablesCount(element.getChildren());
            variablesCount += result.component1();
            referencesCount += result.component2();
            exceptionsCount += result.component3();
        }

        return new Triple<>(variablesCount, referencesCount, exceptionsCount);
    }

    @NotNull
    private String getVariablesCountString(@NotNull Triple<Integer, Integer, Integer> triple)
    {
        return "Variables count: " + triple.component1() + "\n" +
                "References count: " + triple.component2() + "\n" +
                "Exceptions count: " + triple.component3() + "\n";
    }

    private int dfs(PsiElement element, int time)
    {
        astNodeDfsTimeInTimeOut.put(element, new Pair<>(time, time));
        time++;
        PsiElement[] elements = element.getChildren();
        for (PsiElement next : elements)
        {
            time = dfs(next, time);
        }
        astNodeDfsTimeInTimeOut.replace(element, new Pair<>(astNodeDfsTimeInTimeOut.get(element).getKey(), time));
        time++;

        return time;
    }

    private void fillTimeInAndTimeOut(PsiFile psiFile)
    {
        astNodeDfsTimeInTimeOut = new HashMap<>();
        PsiElement[] elements = psiFile.getChildren();
        int time = 0;
        for (PsiElement element : elements)
        {
            time = dfs(element, time);
        }
    }

    private boolean isParent(PsiElement first, PsiElement second)
    {
        return (astNodeDfsTimeInTimeOut.get(first).getKey() < astNodeDfsTimeInTimeOut.get(second).getKey() &&
                astNodeDfsTimeInTimeOut.get(first).getValue() > astNodeDfsTimeInTimeOut.get(second).getValue());
    }

    private PsiElement getLCA(PsiElement first, PsiElement second)
    {
        if (isParent(first, second))
            return first;
        if (isParent(second, first))
            return second;
        return getLCA(first.getParent(), second);
    }

    private int getCorrectSelectionEnd(Editor editor)
    {
        int i = editor.getSelectionModel().getSelectionEnd();
        if (!(i < editor.getDocument().getText().length()))
            i--;
        while (editor.getDocument().getText().charAt(i) == ' ' ||
                editor.getDocument().getText().charAt(i) == '\n')
            i--;

        return i;
    }

    private PsiElement[] getElementsInCurrentDepth(PsiElement[] elements, PsiElement currentElement)
    {
        for (PsiElement element : elements) {
            if (element == currentElement)
                return elements;
            PsiElement[] res = getElementsInCurrentDepth(element.getChildren(), currentElement);
            if (res != null)
                return res;
        }
        return null;
    }

    private PsiElement[] getSelectedElements(@NotNull PsiElement first, @NotNull PsiElement second, PsiElement lca, PsiFile psiFile)
    {
        while (first.getParent() != lca)
            first = first.getParent();
        while (second.getParent() != lca)
            second = second.getParent();

        PsiElement[] allElementsInDepth = getElementsInCurrentDepth(psiFile.getChildren(), first);
        ArrayList<PsiElement> selectedElements = new ArrayList<>();
        boolean elemIsBetween = false;
        assert allElementsInDepth != null;
        for (PsiElement element : allElementsInDepth)
        {
            if (element == first)
                elemIsBetween = true;
            if (elemIsBetween)
                selectedElements.add(element);
            if (element == second)
                break;
        }

        return selectedElements.toArray(PsiElement[]::new);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }

        final PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(StdLanguages.JAVA, editor.getDocument().getText());

        fillTimeInAndTimeOut(psiFile);
        PsiElement firstSelected = psiFile.findElementAt(max(editor.getSelectionModel().getSelectionStart() - 1, 0));
        PsiElement lastSelected = psiFile.findElementAt(getCorrectSelectionEnd(editor));
        PsiElement lca = getLCA(firstSelected, lastSelected);
        assert firstSelected != null;
        assert lastSelected != null;
        PsiElement[] selectedElements = getSelectedElements(firstSelected, lastSelected, lca, psiFile);

        Messages.showInfoMessage(getVariablesCountString(getVariablesCount(selectedElements)) + '\n' +
                        getStringFromTree(selectedElements),"AST");
    }

}
