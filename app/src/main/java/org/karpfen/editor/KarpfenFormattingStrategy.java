package org.karpfen.editor;

import org.eclipse.jface.text.formatter.IFormattingStrategy;
import org.karpfen.serializer.KarpfenDslFormatter;

public class KarpfenFormattingStrategy implements IFormattingStrategy {

	private final String fileExtension;

	public KarpfenFormattingStrategy(String fileExtension) {
		this.fileExtension = fileExtension;
	}

	@Override
	public void formatterStarts(String initialIndentation) {
	}

	@Override
	public String format(String content, boolean isLineStart, String indentation, int[] positions) {
		if (content == null || content.isBlank()) {
			return content;
		}
		return KarpfenDslFormatter.formatAuto(content, fileExtension);
	}

	@Override
	public void formatterStops() {
	}
}
