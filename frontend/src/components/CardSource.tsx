import type { CardCatalogueEntry } from "../types/RewardSelection";

type Props = {
  entry: CardCatalogueEntry | undefined;
};

export default function CardSource({ entry }: Props) {
  if (!entry) return null;

  // Only link to https sources.
  const url =
    entry.officialUrl && /^https:\/\//i.test(entry.officialUrl)
      ? entry.officialUrl
      : null;

  return (
    <p className="card-source">
      <span className={`source-badge ${entry.demo ? "is-demo" : "is-real"}`}>
        {entry.demo
          ? "Demo card: illustrative, not a real product"
          : "Real issuer card"}
      </span>

      {url && (
        <a href={url} target="_blank" rel="noopener noreferrer">
          Issuer terms
        </a>
      )}

      {entry.lastVerifiedAt && (
        <span>Terms last verified {entry.lastVerifiedAt}</span>
      )}
    </p>
  );
}
