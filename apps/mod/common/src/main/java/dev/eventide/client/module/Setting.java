package dev.eventide.client.module;

import dev.eventide.client.Config;
import java.util.Locale;

/**
 * One option on a module's settings page. Values are saved as text in the active profile, keyed
 * by "module.setting", so adding or removing settings never breaks an existing config.
 */
public abstract class Setting {
	public final String id;
	public final String name;
	public final String description;
	Module owner;
	private boolean hidden = false;
	private String group = null;

	protected Setting(String id, String name, String description) {
		this.id = id;
		this.name = name;
		this.description = description;
	}

	public Module owner() {
		return owner;
	}

	/** Leaves this setting off the settings page: data a module's own controls edit, like the Knives slots. */
	@SuppressWarnings("unchecked")
	<T extends Setting> T hide() {
		hidden = true;
		return (T) this;
	}

	public boolean hidden() {
		return hidden;
	}

	/**
	 * Files this setting under a heading on the settings page, like Viewmodel or Advanced; settings that
	 * follow it in the module share the heading until another is set. Advanced starts folded away.
	 */
	@SuppressWarnings("unchecked")
	<T extends Setting> T group(String name) {
		group = name;
		return (T) this;
	}

	/** The heading this setting sits under, or null for the module's main settings. */
	public String group() {
		return group;
	}

	private String key;

	protected String key() {
		String made = key;
		if (made == null) {
			made = owner.id + "." + id;
			key = made;
		}
		return made;
	}

	private String rawNow;
	private int rawVersion = -1;

	protected String raw() {
		int version = Config.version();
		if (version != rawVersion) {
			rawNow = Config.get().settings.get(key());
			rawVersion = version;
		}
		return rawNow;
	}

	protected void store(String value) {
		Config.get().settings.put(key(), value);
		Config.changed();
		Config.saveSoon();
	}

	public void reset() {
		Config.get().settings.remove(key());
		Config.changed();
		Config.saveSoon();
	}

	/** An on/off switch. */
	public static final class Toggle extends Setting {
		private final boolean def;

		public Toggle(String id, String name, String description, boolean def) {
			super(id, name, description);
			this.def = def;
		}

		public boolean get() {
			String raw = raw();
			return raw == null ? def : Boolean.parseBoolean(raw);
		}

		public void set(boolean value) {
			store(Boolean.toString(value));
		}

		public void toggle() {
			set(!get());
		}
	}

	/** A number picked with a slider. */
	public static final class Slider extends Setting {
		public final double min;
		public final double max;
		public final double step;
		private final double def;
		private final String unit;

		public Slider(String id, String name, String description, double min, double max, double step, double def,
				String unit) {
			super(id, name, description);
			this.min = min;
			this.max = max;
			this.step = step;
			this.def = def;
			this.unit = unit;
		}

		private String parsedFrom;
		private double parsed;

		public double get() {
			String raw = raw();
			if (raw == null) return def;
			if (raw == parsedFrom) return parsed;
			double value;
			try {
				value = clamp(Double.parseDouble(raw));
			} catch (NumberFormatException e) {
				value = def;
			}
			parsed = value;
			parsedFrom = raw;
			return value;
		}

		public int getInt() {
			return (int) Math.round(get());
		}

		public float getFloat() {
			return (float) get();
		}

		public void set(double value) {
			store(Double.toString(clamp(Math.round(value / step) * step)));
		}

		/** 0..1 along the slider. */
		public double fraction() {
			return max <= min ? 0 : (get() - min) / (max - min);
		}

		public void setFraction(double fraction) {
			set(min + Math.max(0, Math.min(1, fraction)) * (max - min));
		}

		public String format() {
			double value = get();
			String number = step >= 1 ? Long.toString(Math.round(value))
					: String.format(Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", value);
			return number + unit;
		}

		private double clamp(double value) {
			return Math.max(min, Math.min(max, value));
		}
	}

	/** One of a few named options, clicked through in order. */
	public static final class Choice extends Setting {
		private final String[] options;
		private final int def;

		public Choice(String id, String name, String description, int def, String... options) {
			super(id, name, description);
			this.options = options;
			this.def = def;
		}

		private String indexedFrom;
		private int indexed;

		public int index() {
			String raw = raw();
			if (raw == null) return def;
			if (raw == indexedFrom) return indexed;
			int found = def;
			for (int i = 0; i < options.length; i++) {
				if (options[i].equals(raw)) {
					found = i;
					break;
				}
			}
			indexed = found;
			indexedFrom = raw;
			return found;
		}

		public String get() {
			return options[index()];
		}

		public java.util.List<String> options() {
			return java.util.List.of(options);
		}

		public boolean is(String option) {
			return get().equals(option);
		}

		/** Whether option number {@code i} is picked (no string compare, for hot paths). */
		public boolean isIndex(int i) {
			return index() == i;
		}

		public void next() {
			store(options[(index() + 1) % options.length]);
		}

		public void previous() {
			store(options[(index() + options.length - 1) % options.length]);
		}

		/** Picks an option by name; does nothing for a name that isn't one of the options. */
		public void set(String option) {
			for (String each : options) {
				if (each.equals(option)) store(each);
			}
		}
	}

	/** A short piece of text the player types, like the name Name Protect shows. */
	public static final class Text extends Setting {
		private final String def;
		public final int maxLength;
		private java.util.function.Predicate<String> check = null;
		private String problem = null;

		/** Marks text that won't work, with what to tell the player; empty text always passes (it means the default). */
		Text validate(java.util.function.Predicate<String> check, String problem) {
			this.check = check;
			this.problem = problem;
			return this;
		}

		/** What's wrong with the text as it stands, or null when it's fine. */
		public String problem() {
			String value = get().trim();
			if (check == null || value.isEmpty() || check.test(value)) return null;
			return problem;
		}

		public Text(String id, String name, String description, String def, int maxLength) {
			super(id, name, description);
			this.def = def;
			this.maxLength = maxLength;
		}

		public String get() {
			String raw = raw();
			return raw == null ? def : raw;
		}

		/** The value, or the default when the player cleared the field. */
		public String getOrDefault() {
			String value = get().trim();
			return value.isEmpty() ? def : value;
		}

		public void set(String value) {
			store(value.length() > maxLength ? value.substring(0, maxLength) : value);
		}
	}

	/** A colour picked from the Eventide swatches. */
	public static final class Color extends Setting {
		/** ARGB swatches: the banner blues first, then the usual PvP colours. */
		public static final int[] SWATCHES = {
				0xFFE3F3FC, 0xFF5BC8E6, 0xFF1A4FBA, 0xFF74D46E, 0xFFFFD166, 0xFFFF9F43, 0xFFFF6B81, 0xFFFF5FA2,
				0xFFC77DFF, 0xFF000000
		};
		private final int def;

		public Color(String id, String name, String description, int def) {
			super(id, name, description);
			this.def = def;
		}

		private String parsedFrom;
		private int parsed;

		public int get() {
			String raw = raw();
			if (raw == null) return def;
			if (raw == parsedFrom) return parsed;
			int value;
			try {
				value = (int) Long.parseLong(raw, 16);
			} catch (NumberFormatException e) {
				value = def;
			}
			parsed = value;
			parsedFrom = raw;
			return value;
		}

		public void set(int argb) {
			store(Integer.toHexString(argb));
		}
	}
}
