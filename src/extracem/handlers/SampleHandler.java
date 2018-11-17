package extracem.handlers;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

@SuppressWarnings("restriction")
public class SampleHandler extends AbstractHandler {

	private static IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

	
	public static void showmeall () throws CoreException {
		for (IProject p : root.getProjects()) {
			p.open(null);
			IJavaProject javaProject = JavaCore.create(p);
			IProjectDescription description = p.getDescription();
			description.setNatureIds(new String[] { JavaCore.NATURE_ID });
			p.setDescription(description, null);
			
			for (IPackageFragment pf : javaProject.getPackageFragments()) {
				pf.open(new NullProgressMonitor());
				for (ICompilationUnit cu : pf.getCompilationUnits()) {
//					System.out.println(cu);
				}
			}
		}
	}
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			extractMethodRefactoring();
		} catch (CoreException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void extractMethodRefactoring() throws CoreException, InterruptedException {
		
		IProject[] projects = root.getProjects();
		
		for (IProject p : projects) {refactorProject(p);}
	}

	private static void refactorProject(IProject m_project)
			throws CoreException, JavaModelException, InterruptedException {
		m_project.open(null);
		IJavaProject javaProject = JavaCore.create(m_project);
		IProjectDescription description = m_project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		m_project.setDescription(description, null);
		IPackageFragment[] pfs = javaProject.getPackageFragments();
		for (IPackageFragment pf : pfs) {refactorPackage( pf);}
			
	}

	private static void refactorPackage(IPackageFragment fragment)
			throws JavaModelException, CoreException, InterruptedException {
		NullProgressMonitor pm = new NullProgressMonitor();
		fragment.open(pm);

		ICompilationUnit[] cus = fragment.getCompilationUnits();
		for (ICompilationUnit cu : cus) {refactorCu(cu, pm);}
	}

	private static void refactorCu(ICompilationUnit cu, NullProgressMonitor pm) throws CoreException, InterruptedException {
		List<int[]> ranges = getExpRanges(cu);
		List<Change> changes = new ArrayList<>();
		for (int[] sls : ranges) {changes.add(refactorMethod(cu, sls[0], sls[1]));}
		Change[] cs = new Change[changes.size()];
		cs = changes.toArray(cs);
		
		for (Change c : changes) {
			c.initializeValidationData(pm);
			Change undo = c.perform(new SubProgressMonitor(pm,1));
//			System.out.println("poop");
			cu.open(pm);
//			System.out.println(cu);
			getExtracted(cu);
			undo.initializeValidationData(new SubProgressMonitor(pm,1));
			undo.perform(pm);
		}
	}

	private static Change refactorMethod(ICompilationUnit cu, int start, int length) throws CoreException {
		ExtractMethodRefactoring refactoring = new ExtractMethodRefactoring(cu, start, length);
		//refactoring.setMethodName("extracted" + String.valueOf(start) + String.valueOf(length));
		refactoring.setMethodName("extracted");
		// refactoring.setVisibility(Modifier.DEFAULT);
		NullProgressMonitor pm = new NullProgressMonitor();
		refactoring.checkAllConditions(pm);
		return refactoring.createChange(pm);
	}

	private static List<int[]> getExpRanges(ICompilationUnit cu) {
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setSource(cu);
		CompilationUnit result = (CompilationUnit) parser.createAST(null);
		
		
		RefactorVisitor rv = new RefactorVisitor();
		result.accept(rv);
		
		return rv.getRanges();
		
	}
	
	static void getExtracted(ICompilationUnit cu) {
		ICompilationUnit[] cus = {cu};
		SearchEngine se = new SearchEngine(cus);
		SearchPattern sp = SearchPattern.createPattern("extracted() int", IJavaSearchConstants.METHOD, IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);
		SearchParticipant[] spar = {SearchEngine.getDefaultSearchParticipant()};
		IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
		SearchRequestor requestor = new SearchRequestor() {
			public void acceptSearchMatch(SearchMatch match) {
				IMethod m = (IMethod) match.getElement();
//				try {
//					System.out.println(m.getSource());
//				} catch (JavaModelException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				
				try {
					String src = getJPFcu(m);
					PrintWriter out = new PrintWriter("/Users/remywang/jpf-symbc/src/examples/ByteTest.java");
					out.println(src);
					out.close();
					
					Runtime r = Runtime.getRuntime();
					Process c = null;
					String[] cmd = {"/usr/local/bin/ant","build"};
					ProcessBuilder pb = new ProcessBuilder(cmd);
					pb.directory(new File("/Users/remywang/jpf-symbc/"));
					c = pb.start();
					c.waitFor();
					
					Process p = r.exec("/Users/remywang/jpf-core/bin/jpf /Users/remywang/jpf-symbc/src/examples/ByteTest.jpf");
//					Process p = r.exec("echo yoyo");
					p.waitFor();
					BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
					String l = "";
					String line = "";

					while ((l = b.readLine()) != null) {
						line = line.concat(l);
					}
					
					if (line.contains("AssertionError")) {
						  System.out.println("bad");
					  } else {
						  System.out.println("good");
					  }

					b.close();
				} catch (JavaModelException e) {
					e.printStackTrace();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		try {
			se.search(sp, spar, scope, requestor, null);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	static String getJPFcu(IMethod m) throws JavaModelException {
		
		String mstr = m.getSource();
		
		String src ="import gov.nasa.jpf.symbc.Debug;\n" + 
				"public class ByteTest {\n" + 
				"        public static int f() {\n" + 
				"                return 1;\n" + 
				"        }\n" + 
				"\n"+
				"static "+ mstr +
				"\n" + 
				"        public static void test(int x) {\n" + 
				"                if (f() != extracted())\n" + 
				"                        assert false;\n" + 
				"        }\n" + 
				"\n" + 
				"        public static void main(String[] args) {\n" + 
				"                int b1=Debug.makeSymbolicInteger(\"b1\");\n" + 
				"                test(b1);\n" + 
				"        }\n" + 
				"}";

//		System.out.println(src);		
		return src;
	}
	
	public static ASTNode getASTNode(ICompilationUnit unit) {
	      ASTParser parser = ASTParser.newParser(AST.JLS8);
	      parser.setKind(ASTParser.K_COMPILATION_UNIT);
	      parser.setSource(unit);
	      parser.setResolveBindings(true);
	      return parser.createAST(null);
	   }
}

class RefactorVisitor extends ASTVisitor {
	
	public RefactorVisitor() {
		ranges = new ArrayList<>();
	}
	
	private List<int[]> ranges;	
		
	@Override
	public boolean visit(NumberLiteral node) {
		int start = node.getStartPosition();
		int length = node.getLength();
		int[] range = {start, length};
		ranges.add(range);
		return super.visit(node);
	}
	
	public List<int[]> getRanges() {return ranges;}
}